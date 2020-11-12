import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.*;

class ListPointer {
    private String word;
    private BufferedInputStream invertedIndexBufferedInputStream;
    private boolean isDocIdBlockUncompressed;
    private boolean isFreqBlockUncompressed;
    private int occurrence;
    private int blockNum;
    private int currentBlockIndex;
    private int currentInBlockIndex;
    private int prevLastDocId;
    private int [] uncompressedDocIdBlock;
    private int [] uncompressedFreqBlock;
    private int [] lastDocIds;
    private int [] docIdSizes;
    private int [] freqSizes;


    int [] readMetadataIntArray(BufferedInputStream invertedIndexBufferedInputStream, int blockNum) {

        byte [] metaDataByteBuffer = new byte [blockNum * 4];
        try { invertedIndexBufferedInputStream.read(metaDataByteBuffer); }
        catch (IOException ioe) {ioe.printStackTrace();}
        int [] metadataIntArray = new int [blockNum];
        int arrayIndex = 0;
        for (int i = 0; i < blockNum * 4; i = i + 4) {
            int x = 0;
            x += (metaDataByteBuffer[i] & 0xff) << 24;
            x += (metaDataByteBuffer[i+1] & 0xff) << 16;
            x += (metaDataByteBuffer[i+2] & 0xff) << 8;
            x += (metaDataByteBuffer[i+3] & 0xff) << 0;
            metadataIntArray[arrayIndex++] = x;
        }
        return metadataIntArray;
    }

    ListPointer(String word, long occurrence, BufferedInputStream invertedIndexBufferedInputStream) {
        this.word = word;
        this.invertedIndexBufferedInputStream = invertedIndexBufferedInputStream;
        this.occurrence = (int)occurrence;
        this.blockNum = (int)Math.ceil((occurrence/(double)128));
        this.currentBlockIndex = 0;
        this.currentInBlockIndex = 0;
        this.prevLastDocId = 0;
        this.isDocIdBlockUncompressed = false;
        this.isFreqBlockUncompressed = false;
        this.uncompressedDocIdBlock = null;
        this.uncompressedFreqBlock = null;
        this.lastDocIds = readMetadataIntArray(invertedIndexBufferedInputStream,blockNum);
        this.docIdSizes = readMetadataIntArray(invertedIndexBufferedInputStream,blockNum);
        this.freqSizes = readMetadataIntArray(invertedIndexBufferedInputStream,blockNum);
    }

    String getWord() { return word; }

    BufferedInputStream getInvertedIndexBufferedInputStream() { return invertedIndexBufferedInputStream; }

    int getOccurrence() { return occurrence; }

    int getBlockNum() { return blockNum; }

    int getCurrentBlockIndex() { return currentBlockIndex; }

    int getCurrentInBlockIndex() { return currentInBlockIndex; }

    int getPrevLastDocId() { return prevLastDocId; }

    boolean isDocIdBlockUncompressed() { return isDocIdBlockUncompressed; }

    boolean isFreqBlockUncompressed() { return isFreqBlockUncompressed; }

    int[] getUncompressedDocIdBlock() { return uncompressedDocIdBlock; }

    int[] getUncompressedFreqBlock() { return uncompressedFreqBlock; }

    int[] getLastDocIds() { return lastDocIds; }

    int[] getDocIdSizes() { return docIdSizes; }

    int[] getFreqSizes() { return freqSizes; }

    void setCurrentBlockIndex(int currentBlockIndex) {
        this.currentBlockIndex = currentBlockIndex;
    }

    void setCurrentInBlockIndex(int currentInBlockIndex) {
        this.currentInBlockIndex = currentInBlockIndex;
    }

    void setPrevLastDocId(int prevLastDocId) {
        this.prevLastDocId = prevLastDocId;
    }

    void setDocIdBlockUncompressed(boolean docIdBlockUncompressed) {
        this.isDocIdBlockUncompressed = docIdBlockUncompressed;
    }

    void setUncompressedDocIdBlock(int[] uncompressedDocIdBlock) {
        this.uncompressedDocIdBlock = uncompressedDocIdBlock;
    }

    public void setFreqBlockUncompressed(boolean freqBlockUncompressed) {
        isFreqBlockUncompressed = freqBlockUncompressed;
    }

    public void setUncompressedFreqBlock(int[] uncompressedFreqBlock) {
        this.uncompressedFreqBlock = uncompressedFreqBlock;
    }

    int [] uncompressDocIdBlock() {
        if(!isDocIdBlockUncompressed()) {
            setDocIdBlockUncompressed(true);
            setCurrentInBlockIndex(0);
            byte [] docIdBlockBytes = new byte[getDocIdSizes()[getCurrentBlockIndex()]];
            try{getInvertedIndexBufferedInputStream().read(docIdBlockBytes);}
            catch (IOException ioe) {ioe.printStackTrace();}
            setUncompressedDocIdBlock(VarByte.restoreList(VarByte.VBDecode(docIdBlockBytes),getPrevLastDocId()));
        }
        return getUncompressedDocIdBlock();
    }

    int [] uncompressFreqBlock() {
        if(!isFreqBlockUncompressed()) {
            setFreqBlockUncompressed(true);
            byte [] freqBlockBytes = new byte[getFreqSizes()[getCurrentBlockIndex()]];
            try{getInvertedIndexBufferedInputStream().read(freqBlockBytes);}
            catch (IOException ioe) {ioe.printStackTrace();}
            setUncompressedFreqBlock(VarByte.VBDecode(freqBlockBytes));
        }
        return getUncompressedFreqBlock();
    }

    void printInfo() {
        System.out.println("word: "+ this.word);
        System.out.println("lastDocIds: " + Arrays.toString(lastDocIds));
        System.out.println("docIdSizes: " + Arrays.toString(docIdSizes));
        System.out.println("freqSizes: " + Arrays.toString(freqSizes));
        System.out.println("uncompressedDocIdBlock: " + Arrays.toString(uncompressedDocIdBlock));
        System.out.println("currentBlockIndex: " + currentBlockIndex);
        System.out.println("currentInBlockIndex: " + currentInBlockIndex);
        System.out.println();
    }

}
