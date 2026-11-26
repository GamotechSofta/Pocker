#!/bin/sh
set -eu
mongosh --host 127.0.0.1 --quiet --norc --username "$MONGO_INITDB_ROOT_USERNAME" \
  --password "$(cat /run/secrets/mongo_root_password)" --authenticationDatabase admin --eval '
  const state = db.hello();
  if (!state.setName && !state.isreplicaset) quit(1);
  try {
    rs.status();
  } catch (error) {
    if (error.code !== 94) quit(1);
    try {
      rs.initiate({_id: "poker-staging", members: [{_id: 0, host: "mongo:27017"}]});
    } catch (initializationError) {
      if (initializationError.code !== 23) quit(1);
    }
  }
  const current = db.hello();
  if (current.setName !== "poker-staging" || !current.isWritablePrimary) quit(1);
  ' >/dev/null 2>&1
