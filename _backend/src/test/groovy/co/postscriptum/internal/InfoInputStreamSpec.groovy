package co.postscriptum.internal

import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import spock.lang.Specification

class InfoInputStreamSpec extends Specification {

    def "should calculate proper metrics"() {
        when:
        InfoInputStream iis = new InfoInputStream(new ByteArrayInputStream("test123".getBytes()))
        IOUtils.copy(iis, new NullOutputStream())

        then:
        iis.getSize() == 7L
        iis.getSha1() == "7288edd0fc3ffcbe93a0cf06e3568e28521687bc"

        cleanup:
        iis.close()
    }

}
