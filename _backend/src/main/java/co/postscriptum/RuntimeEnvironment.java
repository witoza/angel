package co.postscriptum;

public enum RuntimeEnvironment {

    DEV,
    CERT,
    PROD;

    public String getDomain() {
        if (this == RuntimeEnvironment.PROD) {
            return "postscriptum.co";
        } else {
            return this.toString().toLowerCase() + ".postscriptum.co";
        }
    }

}
