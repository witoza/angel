package co.postscriptum.metrics

import spock.lang.Specification

class JVMMetricsSpec extends Specification {

    def 'should dump JVMMetrics'() {
        given:
        JVMMetrics jvmMetrics = new JVMMetrics()

        expect:
        println jvmMetrics.dump()
        jvmMetrics.dump().contains("active threads")
    }

}
