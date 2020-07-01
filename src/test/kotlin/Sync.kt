import org.freechains.cli.main_cli
import org.freechains.cli.main_cli_assert
import org.freechains.common.PORT_8330
import org.freechains.common.assert_
import org.freechains.common.listSplit
import org.freechains.host.main_host
import org.freechains.store.Store
import org.freechains.sync.CBS
import org.freechains.sync.Sync
import org.freechains.sync.main_sync
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import kotlin.concurrent.thread


private const val PATH = "/tmp/freechains/tests/sync/"
private const val PVT0 = "6F99999751DE615705B9B1A987D8422D75D16F5D55AF43520765FA8C5329F7053CCAF4839B1FDDF406552AF175613D7A247C5703683AEC6DBDF0BB3932DD8322"
private const val PUB0 = "3CCAF4839B1FDDF406552AF175613D7A247C5703683AEC6DBDF0BB3932DD8322"
private const val SIG0 = "--sign=$PVT0"

private fun path (i: Int) : String {
    return "$PATH/$i"
}

private fun port (i: Int) : String {
    return "--port=${PORT_8330+i}"
}
private fun myself (i: Int) : String {
    return "--host=localhost:${PORT_8330+i}"
}
private fun pair (i: Int) : String {
    return "localhost:${PORT_8330+i}"
}

@TestMethodOrder(Alphanumeric::class)
class Tests_Sync {

    companion object {
        @BeforeAll
        @JvmStatic
        internal fun reset () {
            assert_(File(PATH).deleteRecursively())
        }
    }

    @BeforeEach
    fun stop () {
        //main_host(arrayOf("start", (PORT_8330+0).toString()))
        //main_host(arrayOf("start", (PORT_8330+1).toString()))
        //main_host(arrayOf("start", (PORT_8330+2).toString()))
    }

    @Test
    fun s00 () {
        thread { main_host(arrayOf("start", path(10), port(10))) }
        Thread.sleep(200)

        main_cli_assert(arrayOf(myself(10), "chains", "join", "#store"))
        val s = Store("#store", PORT_8330+10)

        var ok = false
        fun cb (v1: String, v2: String, v3: String) {
            ok = true
            assert_(v1=="v1" && v2=="v2" && v3=="v3")
        }
        s.cbs.add(::cb)

        s.store("v1","v2","v3")
        Thread.sleep(200)
        assert_(ok)
        assert(s.data["v1"]!!["v2"]!! == "v3")
        s.cbs.remove(::cb)

        s.store("v1","v2","REM")
        assert_(!s.data["v1"]!!.containsKey("v2"))

        s.cbs.add { v1,v2,v3 ->
            if (v1 == "chains") {
                when {
                    (v3 == "REM") -> main_cli(arrayOf(s.port_, "chains", "leave", v2))
                    (v3 == "ADD") -> main_cli(arrayOf(s.port_, "chains", "join",  v2))
                    else          -> main_cli(arrayOf(s.port_, "chains", "join",  v2, v3))
                }
            }
        }

        s.store("chains","#xxx","ADD")
        Thread.sleep(100)
        assert_(main_cli_assert(arrayOf(myself(10), "chains", "list")).listSplit().contains("#xxx"))
    }

    @Test
    fun s01 () {
        thread { main_host(arrayOf("start", path(11), port(11))) }
        Thread.sleep(200)

        main_cli_assert(arrayOf(myself(10), "chains", "join", "@$PUB0"))
        main_cli_assert(arrayOf(myself(11), "chains", "join", "@$PUB0"))

        thread { main_sync(arrayOf(port(10), "@$PUB0")) }
        main_cli_assert(arrayOf(myself(10), SIG0, "chain", "@$PUB0", "post", "inline", "chains \"@$PUB0\" ADD"))
        Thread.sleep(200)

        main_cli_assert(arrayOf(myself(10), "peer", pair(11), "send", "@$PUB0"))
        Thread.sleep(200)
        thread { main_sync(arrayOf(port(11), "@$PUB0")) }

        main_cli_assert(arrayOf(myself(10), SIG0, "chain", "@$PUB0", "post", "inline", "peers ${pair(11)} ADD"))
        main_cli_assert(arrayOf(myself(10), SIG0, "chain", "@$PUB0", "post", "inline", "chains #chat ADD"))

        Thread.sleep(200) // #chat ADD has to propagate to @11

        main_cli_assert(arrayOf(myself(10), "chain", "#chat", "post", "inline", "[#chat] Hello World!"))

        Thread.sleep(200) // "Hello World! has to propagate to @11

        main_cli_assert(arrayOf(myself(11), "chain", "#chat", "heads", "all")).let {
            //println(it)
            val pay = main_cli_assert(arrayOf(myself(11), "chain", "#chat", "get", "payload", it))
            assert_(pay == "[#chat] Hello World!")
        }
    }

    @Test
    fun t00 () {
        thread { main_host(arrayOf("start", path(0), port(0))) }      // peer with bootstrap
        thread { main_host(arrayOf("start", path(1), port(1))) }      // peer with all content
        thread { main_host(arrayOf("start", path(2), port(2))) }      // myself
        Thread.sleep(200)

        val KEY = main_cli_assert(arrayOf("crypto", "shared", "my-password", myself(2)))

        main_cli_assert(arrayOf("chains", "join", "\$bootstrap.xxx", KEY, myself(0)))
        main_cli_assert(arrayOf("chains", "join", "#chat", myself(1)))
        main_cli_assert(arrayOf("chains", "join", "\$family", KEY, myself(1)))
        main_cli_assert(arrayOf("chains", "join", "\$bootstrap.xxx", KEY, myself(2)))

        main_cli_assert(arrayOf("chain", "#chat",    "post", "inline", "[#chat] Hello World!",    myself(1)))
        main_cli_assert(arrayOf("chain", "\$family", "post", "inline", "[\$family] Hello World!", myself(1)))

        // post to 8330 -- send to --> 8332
        main_cli_assert(arrayOf(myself(0), "chain", "\$bootstrap.xxx", "post", "inline", "peers ${pair(1)} ADD"))
        main_cli_assert(arrayOf(myself(0), "chain", "\$bootstrap.xxx", "post", "inline", "chains #chat ADD"))
        main_cli_assert(arrayOf("peer", pair(2), "send", "\$bootstrap.xxx", myself(0)))

        val store = Store("\$bootstrap.xxx", PORT_8330+2)
        Sync(store, CBS)
        Thread.sleep(200)
        assert (
                store.data["peers"]!!.contains(pair(1)) &&
                        main_cli_assert(arrayOf(port(2), "chains", "list")).listSplit().contains("#chat")
        )

        main_cli_assert(arrayOf("chain", "#chat", "heads", "all", myself(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "#chat", "get", "payload", it, myself(2)))
            assert_(pay == "[#chat] Hello World!")
        }

        main_cli_assert(arrayOf(port(2), "chain", "\$bootstrap.xxx", "post", "inline", "chains \$family $KEY"))
        Thread.sleep(200)
        main_cli_assert(arrayOf("chain", "\$family", "heads", "all", myself(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "\$family", "get", "payload", it, myself(2)))
            assert_(pay == "[\$family] Hello World!")
        }
    }

    @Test
    fun t01 () {
        thread { main_host(arrayOf("start", path(3), port(3))) }
        Thread.sleep(200)
        thread {
            val KEY = main_cli_assert(arrayOf("crypto", "shared", "my-password", myself(2)))
            main_cli_assert(arrayOf(port(3), "chains", "join", "\$bootstrap.xxx", KEY))
            main_cli_assert(arrayOf(port(3), "peer", pair(2), "recv", "\$bootstrap.xxx"))
            main_sync(arrayOf(port(3), "\$bootstrap.xxx"))
        }

        Thread.sleep(200)
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