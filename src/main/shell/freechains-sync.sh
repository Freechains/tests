#!/usr/bin/env sh
exec nice -n 19 java -Xmx5M -Xms5M -ea -cp "$(dirname "$0")"/Freechains.jar org.freechains.sync.MainKt "$@"