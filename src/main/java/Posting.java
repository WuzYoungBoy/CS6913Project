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

    public String getWord() {
        return word;
    }

    public String getDocNumStr() {
        return docNumStr;
    }

    public ArrayList<String> getPositions() {
        return positions;
    }
}
