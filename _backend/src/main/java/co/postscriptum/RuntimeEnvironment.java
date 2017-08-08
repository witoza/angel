package co.postscriptum;

public enum RuntimeEnvironment {

    DEV,
    CERT,
    PROD;

    public String getDomain() {
        if (this == PROD) {
            return "postscriptum.co";
        }
        return this.toString().toLowerCase() + ".postscriptum.co";
    }

}
