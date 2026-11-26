const { MongoClient, BSON } = require('mongoose').mongo;

const index = (key, options = {}) => ({
  name: Object.entries(key).map(([field, order]) => `${field}_${order}`).join('_'),
  key,
  ...options,
});
const unique = { unique: true };
const indexes = {
  tournaments: [
    index({ privateInviteCode: 1 }, { unique: true, sparse: true }),
    index({ creatorId: 1 }), index({ status: 1 }),
    index({ status: 1, scheduledStartAt: 1 }), index({ status: 1, createdAt: -1 }),
  ],
  tournament_blind_schedules: [index({ tournamentId: 1 }, unique)],
  tournament_entrants: [
    index({ tournamentId: 1 }), index({ userId: 1 }), index({ status: 1 }),
    index({ finishPosition: 1 }, { sparse: true }),
    index({ tournamentId: 1, userId: 1 }, unique),
    index({ tournamentId: 1, finishPosition: 1 }, {
      name: 'tournament_finished_rank_unique',
      unique: true,
      partialFilterExpression: { finishPosition: { $gte: 1 } },
    }),
  ],
  tournament_table_assignments: [
    index({ tournamentId: 1 }), index({ entrantId: 1 }), index({ tableId: 1 }),
    index({ tournamentId: 1, entrantId: 1 }, unique),
    index({ tournamentId: 1, tableId: 1, seat: 1 }, unique),
  ],
  tournament_final_results: [
    index({ tournamentId: 1 }), index({ entrantId: 1 }),
    index({ tournamentId: 1, entrantId: 1 }, unique),
    index({ tournamentId: 1, finishPosition: 1 }, unique),
  ],
  tournament_chip_ledger: [
    index({ tournamentId: 1 }, { name: 'tournamentId' }),
    index({ entrantId: 1 }, { name: 'entrantId' }),
    index({ tournamentId: 1, idempotencyKey: 1 }, { name: 'chip_ledger_idempotency_unique', unique: true }),
  ],
};

const sampleLimit = 10;
const supportedOptions = new Set(['v', 'key', 'name', 'ns', 'background', 'unique', 'sparse',
  'partialFilterExpression', 'collation', 'hidden']);

function sameKey(left, right) {
  return JSON.stringify(Object.entries(left)) === JSON.stringify(Object.entries(right));
}

function sameOptions(existing, desired) {
  return Object.keys(existing).every(option => supportedOptions.has(option))
    && Boolean(existing.unique) === Boolean(desired.unique)
    && Boolean(existing.sparse) === Boolean(desired.sparse)
    && JSON.stringify(existing.partialFilterExpression) === JSON.stringify(desired.partialFilterExpression)
    && (!existing.collation || existing.collation.locale === 'simple')
    && !existing.hidden;
}

async function inspectUniqueData(collection, desired) {
  const fields = Object.keys(desired.key);
  const indexed = desired.partialFilterExpression ?? (desired.sparse
    ? { $or: fields.map(field => ({ [field]: { $exists: true } })) }
    : {});
  const groupKey = Object.fromEntries(fields.map((field, position) => [`k${position}`, { $ifNull: [`$${field}`, null] }]));
  const arrayExamples = await collection.find({
    $or: fields.map(field => ({ [field]: { $type: 'array' } })),
  }, { projection: { _id: 1 } }).limit(sampleLimit).toArray();
  const duplicateKeys = await collection.aggregate([
    { $match: indexed },
    { $group: { _id: groupKey, count: { $sum: 1 } } },
    { $match: { count: { $gt: 1 } } },
    { $limit: sampleLimit },
  ], { allowDiskUse: true, collation: { locale: 'simple' } }).toArray();
  return { duplicateKeys: duplicateKeys.map(row => ({ count: row.count })), arrayExamples: arrayExamples.map(() => true) };
}

async function inspect(db, manifest = indexes, migration = '001-tournament-indexes') {
  const collections = [];
  for (const [name, desiredIndexes] of Object.entries(manifest)) {
    const [metadata] = await db.listCollections({ name }).toArray();
    const collection = db.collection(name);
    const actual = metadata?.type === 'collection' ? await collection.indexes() : [];
    const namespaceConflict = metadata && (metadata.type !== 'collection' ||
      (metadata.options?.collation && metadata.options.collation.locale !== 'simple'));
    const checks = [];
    for (const desired of desiredIndexes) {
      const related = actual.filter(current => current.name === desired.name || sameKey(current.key, desired.key));
      const matching = related.find(current => current.name === desired.name
        && sameKey(current.key, desired.key) && sameOptions(current, desired));
      const conflicts = related.filter(current => current !== matching);
      const data = desired.unique && metadata?.type === 'collection'
        ? await inspectUniqueData(collection, desired) : { duplicateKeys: [], arrayExamples: [] };
      checks.push({ desired, state: conflicts.length ? 'conflict' : matching ? 'present' : 'missing',
        conflicts, ...data });
    }
    const unmanaged = actual.filter(current => current.name !== '_id_' && !desiredIndexes.some(desired =>
      current.name === desired.name || sameKey(current.key, desired.key)));
    collections.push({ name, ...(namespaceConflict ? { namespaceConflict: metadata } : {}), checks, unmanaged });
  }
  const blocked = collections.some(({ namespaceConflict, checks }) => namespaceConflict
    || checks.some(check => check.state === 'conflict' || check.duplicateKeys.length || check.arrayExamples.length));
  return { migration, database: db.databaseName, sampleLimit, blocked, collections };
}

async function main(manifest, migration) {
  const args = process.argv.slice(2);
  const [mode, database] = args;
  const uri = process.env.TOURNAMENT_INDEX_MONGO_URI;
  if (!['--dry-run', '--apply'].includes(mode) || args.length !== 2 || !database || !uri) {
    throw new Error('Set TOURNAMENT_INDEX_MONGO_URI and specify --dry-run or --apply followed by the database name.');
  }
  const client = new MongoClient(uri, { serverSelectionTimeoutMS: 5000 });
  try {
    await client.connect();
    const db = client.db(database);
    const plan = await inspect(db, manifest, migration);
    console.log(BSON.EJSON.stringify({ mode, ...plan }, null, 2));
    if (plan.blocked) {
      process.exitCode = 2;
      return;
    }
    if (mode === '--apply') {
      for (const { name, checks } of plan.collections) {
        for (const { state, desired: { key, ...options } } of checks) {
          if (state === 'missing') {
            await db.collection(name).createIndex(key, options);
            console.error(`Created ${name}.${options.name}`);
          }
        }
      }
    }
  } finally {
    await client.close();
  }
}

if (require.main === module) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

module.exports = { indexes, index, inspect, main };
