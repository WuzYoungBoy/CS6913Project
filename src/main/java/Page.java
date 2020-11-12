class Page {
    private String URL;
    private int termNum;
    private long startPosition;
    private long endPosition;

    public Page(String URL, int termNum, long startPosition, long endPosition) {
        this.URL = URL;
        this.termNum = termNum;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public String getURL() {
        return URL;
    }

    public int getTermNum() {
        return termNum;
    }

    public long getStartPosition() {
        return startPosition;
    }

    public long getEndPosition() {
        return endPosition;
    }
}
