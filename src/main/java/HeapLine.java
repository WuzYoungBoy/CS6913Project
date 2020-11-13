class HeapLine {

    private String line;
    private int fileBufferIndex;
    private int firstSpace;

    HeapLine(String line, int fileBufferIndex) {
        this.line = line;
        this.fileBufferIndex = fileBufferIndex;
        this.firstSpace = line.indexOf(" ");
    }

    String getLine() {
        return line;
    }

    int getFileBufferIndex() {
        return fileBufferIndex;
    }

    String getWord() {
        return line.substring(0,firstSpace);
    }

    String getDocID() {
        return line.substring(firstSpace + 1, line.indexOf(",", firstSpace + 1));
    }

    String getDocFreqString() {
        return line.substring(firstSpace + 1);
    }

}
