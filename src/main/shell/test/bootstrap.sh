#!/usr/bin/env bash

echo
echo "=== TESTS-BOOTSTRAP ==="
echo

KEY=03F09D95821EABE32055921AFAF6D8584759A86C5E3B3126DD138372C148F47D
FC=/tmp/freechains/bootstrap/
./clean.sh

freechains-host start $FC/0 --port=8400 &
freechains-host start $FC/1 --port=8401 &
freechains-host start $FC/2 --port=8402 &
sleep 1

echo ==========================================================
echo === 1
echo ==========================================================

freechains --port=8400 chains join "\$bootstrap.xxx" $KEY

#freechains --port=8400 chain "\$bootstrap.xxx" post file 1.bootstrap
freechains --port=8400 chain "\$bootstrap.xxx" post inline "peers add localhost:8400"
freechains --port=8400 chain "\$bootstrap.xxx" post inline "peers add localhost:8401"
freechains --port=8400 chain "\$bootstrap.xxx" post inline "chains add #chat"

freechains --port=8401 chains join "#chat"
freechains --port=8401 chains join "\$family" $KEY
freechains --port=8401 chain "#chat"    post inline "[#chat] Hello World!"
freechains --port=8401 chain "\$family" post inline "[\$family] Hello World!"

freechains --port=8402 chains join "\$bootstrap.xxx" $KEY
freechains-bootstrap --port=8402 remote "localhost:8400" "\$bootstrap.xxx" &
BOOT=$!
sleep 1
diff $FC/1/chains/\#chat/blocks/ $FC/2/chains/\#chat/blocks/ || exit 1

echo ==========================================================
echo === 2
echo ==========================================================

kill $BOOT
freechains-bootstrap --port=8402 local "\$bootstrap.xxx" &
sleep 1

freechains --port=8400 chain "\$bootstrap.xxx" post inline "chains add \$family 03F09D95821EABE32055921AFAF6D8584759A86C5E3B3126DD138372C148F47D"
freechains --port=8400 peer localhost:8402 send "\$bootstrap.xxx"
sleep 1
diff $FC/1/chains/\$family/blocks/ $FC/2/chains/\$family/blocks/ || exit 1

echo ==========================================================
echo === 3
echo ==========================================================

freechains-host start $FC/3 --port=8403 &
sleep 1
freechains --port=8403 chains join "\$bootstrap.xxx" $KEY
freechains-bootstrap --port=8403 remote "localhost:8402" "\$bootstrap.xxx" &
sleep 1

freechains --port=8402 chain "\$bootstrap.xxx" post inline "peers add localhost:8403"
freechains --port=8402 chain "\$bootstrap.xxx" post inline "chains add #new"
sleep 1
freechains --port=8402 chain "#new" post inline "#new from 8402"
sleep 20

diff $FC/2/chains/ $FC/3/chains/ || exit 1
grep -r "#new from 8402" $FC/3   || exit 1

echo ==========================================================
echo === 4
echo ==========================================================

freechains --port=8402 chain "\$bootstrap.xxx" post inline "chains rem #new"
sleep 5
freechains --port=8402 chains join "#new"
freechains --port=8402 chain "#new" post inline "#new again from 8402"

sleep 10
! grep -r "#new again from 8402" $FC/3 || exit 1
grep -r "#new again from 8402" $FC/2   || exit 1

freechains --port=8402 chain "\$bootstrap.xxx" post inline "peers rem localhost:8403"
sleep 5
freechains --port=8402 chain "#chat" post inline "#chat from 8402"

sleep 10
! grep -r "#chat from 8402" $FC/3 || exit 1
grep -r "#chat from 8402" $FC/2   || exit 1

echo ==========================================================
echo ==========================================================

./clean.sh

echo
echo "=== ALL TESTS PASSED ==="
echo
