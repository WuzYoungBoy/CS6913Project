import java.util.ArrayList;

public class Posting {
    private String word;
    private String docNumStr;
    private ArrayList<String> positions;

    public Posting(String word, String docNumStr, ArrayList<String> positions) {
        this.word = word;
        this.docNumStr = docNumStr;
        this.positions = positions;
    }

    String getWord() {
        return word;
    }

    String getDocNumStr() {
        return docNumStr;
    }

    ArrayList<String> getPositions() {
        return positions;
    }
}
