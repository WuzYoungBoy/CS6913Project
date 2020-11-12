import org.apache.commons.lang3.StringUtils;
import com.google.common.primitives.Bytes;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;


class BinaryFormatter {



    private void updateBlockMetadata(int bufferNum, int blockIndex, int[] lastDocIds, int [] docIdSizes, int [] freqSizes, int [] docIdBuffer,
                                     ByteBuffer docIdFreqByteBuffer, byte [] encodedDocIdGapByteBlock, byte [] encodedFreqByteBlock) {

        lastDocIds[blockIndex] = docIdBuffer[bufferNum - 1];
        docIdSizes[blockIndex] = encodedDocIdGapByteBlock.length;
        freqSizes[blockIndex] = encodedFreqByteBlock.length;
        docIdFreqByteBuffer.put(encodedDocIdGapByteBlock);
        docIdFreqByteBuffer.put(encodedFreqByteBlock);
    }

    int [] getDocIdsAndFreqs(String line, BufferedOutputStream bos){
        String [] tupleStrs = StringUtils.split(line, ' ');


        int blockNum = (int)Math.ceil((tupleStrs.length / (double)128));
        int [] docIdBuffer = new int [128];
        int [] freqBuffer = new int [128];
        int [] lastDocIds = new int [blockNum];
        int [] docIdSizes = new int [blockNum];
        int [] freqSizes = new int[blockNum];

        int bufferNum = 0;
        int blockIndex = 0;
        int prevInt = 0;
        int firstDotComma;

        ByteBuffer metadataArrayByteBuffer = ByteBuffer.allocate(blockNum * 3 * (Integer.SIZE / Byte.SIZE));
        ByteBuffer docIdFreqByteBuffer = ByteBuffer.allocate(tupleStrs.length * 2 * (Integer.SIZE / Byte.SIZE));


        for (int i = 0; i < tupleStrs.length; i++) {

            if (bufferNum == 128) {
                updateBlockMetadata(bufferNum,blockIndex,lastDocIds, docIdSizes, freqSizes,docIdBuffer, docIdFreqByteBuffer,
                        VarByte.VBEncodeNumberGaps(docIdBuffer,0,bufferNum, prevInt),VarByte.VBEncodeNumbers(freqBuffer));
                prevInt = lastDocIds[blockIndex++];
                bufferNum = 0;
            }

            firstDotComma = tupleStrs[i].indexOf(',');
            docIdBuffer[bufferNum] = Integer.parseInt(tupleStrs[i].substring(0, firstDotComma));
            freqBuffer[bufferNum] = Integer.parseInt(tupleStrs[i].substring(firstDotComma + 1));
            bufferNum++;
        }
        updateBlockMetadata(bufferNum,blockIndex++,lastDocIds, docIdSizes, freqSizes,docIdBuffer, docIdFreqByteBuffer,
                VarByte.VBEncodeNumberGaps(docIdBuffer,0,bufferNum, prevInt),VarByte.VBEncodeNumbers(freqBuffer,0,bufferNum));


        for (int i = 0; i < blockNum; i++) metadataArrayByteBuffer.putInt(lastDocIds[i]);
        for (int i = 0; i < blockNum; i++) metadataArrayByteBuffer.putInt(docIdSizes[i]);
        for (int i = 0; i < blockNum; i++) metadataArrayByteBuffer.putInt(freqSizes[i]);

//        System.out.println("occurrence: " + tupleStrs.length);
//        System.out.println("blockNum: " + blockIndex);
//        System.out.println("lastDocIds: "  + Arrays.toString(lastDocIds));
//        System.out.println("docIdSizes: " +Arrays.toString(docIdSizes));
//        System.out.println("freqSizes: " +Arrays.toString(freqSizes));

//        docIdFreqByteBuffer.flip();
//        byte[] vb  = new byte[docIdFreqByteBuffer.limit()];
//        docIdFreqByteBuffer.get(vb);
//        byte [] docIdTemp1 = Arrays.copyOfRange(vb, 0, 170);
//        byte [] docIdTemp2 = Arrays.copyOfRange(vb, 298, 298 + 164);
//        byte [] freqTemp1 = Arrays.copyOfRange(vb, 170, 290);
//        byte [] freqTemp2 = Arrays.copyOfRange(vb, 298 + 164, 298 + 164 + 128);
//        docIdTemp1= Bytes.concat(docIdTemp1, docIdTemp2);
//        VarByte.printOriginalList(VarByte.VBDecode(docIdTemp1));
//        freqTemp1= Bytes.concat(freqTemp1, freqTemp2);
//        VarByte.printList(VarByte.VBDecode(freqTemp1));


        return new int [] {tupleStrs.length,  WriteToInvertedIndex(bos, metadataArrayByteBuffer, docIdFreqByteBuffer)};

    }

    void WriteToLexicon(BufferedWriter lexbw, String word, String wordOccurrence, String start, String end) throws IOException {
        lexbw.write(word);lexbw.write(' ');
        lexbw.write(wordOccurrence);lexbw.write(' ');
        lexbw.write(start);lexbw.write(' ');
        lexbw.write(end); lexbw.newLine();
    }

    int WriteToInvertedIndex(BufferedOutputStream bos, ByteBuffer metadataArrayByteBuffer, ByteBuffer docIdFreqByteBuffer) {

        metadataArrayByteBuffer.flip();
        byte[] metadata  = new byte[metadataArrayByteBuffer.limit()];
        metadataArrayByteBuffer.get(metadata);

        docIdFreqByteBuffer.flip();
        byte[] docIdFreq  = new byte[docIdFreqByteBuffer.limit()];
        docIdFreqByteBuffer.get(docIdFreq);

        try { bos.write(metadata); bos.write(docIdFreq);}
        catch (IOException ioe) { ioe.printStackTrace();}


        return metadata.length + docIdFreq.length;
    }


    public void generateInvertedIndex(String intermediatePostingPath, String outputPath) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(intermediatePostingPath));
            BufferedWriter lexbw = new BufferedWriter(new FileWriter(outputPath.concat("Lexicon")));
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath.concat("InvertedIndex")));

            String line = br.readLine();
            long start = 0L,  end = 0L;
            int counter = 0;
            System.out.println("Start reformatting");
            while((line = br.readLine()) != null) {
                int firstSpace = line.indexOf(' ');
                String word = line.substring(0,firstSpace);
                int [] occurrenceAndListSize = getDocIdsAndFreqs(line.substring(firstSpace + 1), bos);

                end = start + occurrenceAndListSize[1];
                WriteToLexicon(lexbw, word, ""+occurrenceAndListSize[0], ""+start,""+end);
                start = end;
            }
            lexbw.close();
            bos.close();
        }catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    byte [] read(BufferedInputStream bis, int start, int end) throws IOException{
        byte [] buffer = new byte[end - start];
        bis.skip(start);
        bis.read(buffer);

        return buffer;
    }

}
