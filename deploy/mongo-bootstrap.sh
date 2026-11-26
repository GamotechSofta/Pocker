#!/bin/sh
set -eu
export MONGO_APP_PASSWORD="$(cat /run/secrets/mongo_app_password)"
mongosh --host mongo --quiet --norc --username "$MONGO_ROOT_USERNAME" \
  --password "$(cat /run/secrets/mongo_root_password)" --authenticationDatabase admin --eval '
  const target = db.getSiblingDB(process.env.MONGODB_DB);
  const user = process.env.MONGO_APP_USERNAME;
  if (!target.getUser(user)) {
    target.createUser({user, pwd: process.env.MONGO_APP_PASSWORD, roles: [{role: "readWrite", db: target.getName()}]});
  }
  ' >/dev/null
