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

###############################################################################
echo === 1

freechains --host=localhost:8400 chains join "\$bootstrap.xxx" $KEY
freechains --host=localhost:8400 chain "\$bootstrap.xxx" post file 1.bootstrap

freechains --host=localhost:8401 chains join "#chat"
freechains --host=localhost:8401 chains join "\$family" $KEY
freechains --host=localhost:8401 chain "#chat"    post inline "[#chat] Hello World!"
freechains --host=localhost:8401 chain "\$family" post inline "[\$family] Hello World!"

freechains-bootstrap --port=8402 init "localhost:8400" "\$bootstrap.xxx" $KEY &
BOOT=$!
sleep 1
diff $FC/1/chains/\#chat/blocks/ $FC/2/chains/\#chat/blocks/ || exit 1

###############################################################################
echo === 2

kill $BOOT
freechains-bootstrap --port=8402 &
sleep 1

freechains --host=localhost:8400 chain "\$bootstrap.xxx" post file 2.bootstrap
freechains --host=localhost:8400 peer localhost:8402 send "\$bootstrap.xxx"
sleep 1
diff $FC/1/chains/\$family/blocks/ $FC/2/chains/\$family/blocks/ || exit 1

###############################################################################
echo === 3

freechains-host start $FC/3 --port=8403 &
sleep 1
freechains-bootstrap --port=8403 init "localhost:8402" "\$bootstrap.xxx" $KEY &
sleep 1

freechains --host=localhost:8402 chains join "#new"
freechains --host=localhost:8402 chain "#new" post inline "#new from 8402"
freechains --host=localhost:8402 chain "\$bootstrap.xxx" post file 3.bootstrap
sleep 30

diff $FC/2/chains/ $FC/3/chains/ || exit 1
grep -r "#new from 8402" $FC/3   || exit 1

###############################################################################

echo
echo "=== ALL TESTS PASSED ==="
echo

exit 0

        boot.write { it.chains.add(Pair("\$family", KEY)) }
        Thread.sleep(500)
        main_cli_assert(arrayOf("chain", "\$family", "heads", "all", myself(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "\$family", "get", "payload", it, myself(2)))
            assert_(pay == "[\$family] Hello World!")
        }
    }

    @Test
    fun t01 () {
        thread { main_host(arrayOf("start", path(3), port(3))) }
        Thread.sleep(500)
        thread { main_bootstrap(arrayOf(port(3), "init", pair(2), "\$bootstrap.xxx", KEY!!)) }

        Thread.sleep(1000)
        main_cli_assert(arrayOf(myself(3), "chain", "#chat", "heads", "all")).let {
            val pay = main_cli_assert(arrayOf(myself(3), "chain", "#chat", "get", "payload", it))
            assert_(pay == "[#chat] Hello World!")
        }
        main_cli_assert(arrayOf(myself(3), "chain", "\$family", "heads", "all")).let {
            val pay = main_cli_assert(arrayOf(myself(3), "chain", "\$family", "get", "payload", it))
            assert_(pay == "[\$family] Hello World!")
        }
    }
}
PVT0=6F99999751DE615705B9B1A987D8422D75D16F5D55AF43520765FA8C5329F7053CCAF4839B1FDDF406552AF175613D7A247C5703683AEC6DBDF0BB3932DD8322
PUB0=3CCAF4839B1FDDF406552AF175613D7A247C5703683AEC6DBDF0BB3932DD8322
S0=--sign=$PVT0

PUB1=E14E4D7E152272D740C3CA4298D19733768DF7E74551A9472AAE384E8AB34369
PVT1=6A416117B8F7627A3910C34F8B35921B15CF1AC386E9BB20E4B94AF0EDBE24F4E14E4D7E152272D740C3CA4298D19733768DF7E74551A9472AAE384E8AB34369
S1=--sign=$PVT1

H0=--host=localhost:8400
H1=--host=localhost:8401

###############################################################################
echo "#### 1"

freechains-host start $FC/8400 --port=8400 &
sleep 0.5
freechains $H0 chains join "#"

freechains-host start $FC/8401 --port=8401 &
sleep 0.5
freechains $H1 chains join "#"

freechains-host $H0 now 0
freechains-host $H1 now 0

freechains $H0 $S0 chain "#" post inline zero
freechains $H0 $S1 chain "#" post inline xxxx
freechains $H0 $S0 chain "#" like `freechains $H0 chain "#" heads blocked` --why="like xxxx"

freechains $H0 peer localhost:8401 send "#"

# h0 <- zero <-- lxxxx
#             \- xxxx

freechains $H0 now 90000000
freechains-host $H1 now 90000000

echo ">>> LIKES"

h1111=`freechains $H0 chain "#" post inline 1111`
haaaa=`freechains $H1 chain "#" post inline aaaa`

#                         1111
# h0 <- zero <-- lxxxx <-/
#             \- xxxx <-/ \
#                          aaaa

! diff -q $FC/8400/chains/\#/blocks/ $FC/8401/chains/\#/blocks/ || exit 1

freechains $H0 $S0 chain "#" like $h1111 --why="like 1111"
freechains $H1 $S1 chain "#" like $haaaa --why="like aaaa"

#                         111
# h0 <- zero <-- lxxxx <-/  <-- l111
#             \- xxxx <-/ \ <-- laaa
#                          aaa

freechains-host $H0 now 98000000
freechains-host $H1 now 98000000

freechains $H0 peer localhost:8401 send "#"
freechains $H1 peer localhost:8400 send "#"

diff $FC/8400/chains/\#/blocks/ $FC/8401/chains/\#/blocks/ || exit 1

###############################################################################

echo
echo "=== ALL TESTS PASSED ==="
echo