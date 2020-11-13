class Page {
    private String URL;
    private int wordNumber;
    private long startPosition;
    private long endPosition;

    Page(String URL, int wordNumber, long startPosition, long endPosition) {
        this.URL = URL;
        this.wordNumber = wordNumber;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    String getURL() {
        return URL;
    }

    int getWordNumber() {
        return wordNumber;
    }

    long getStartPosition() {
        return startPosition;
    }

    long getEndPosition() {
        return endPosition;
    }
}
