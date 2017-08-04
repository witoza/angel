package co.postscriptum.metrics

import spock.lang.Specification

class JVMMetricsSpec extends Specification {

    def 'should dump JVMMetrics'() {
        given:
        JVMMetrics jvmMetrics = new JVMMetrics()

        expect:
        jvmMetrics.dump().contains("active threads")
    }

}
