import org.apache.commons.lang3.StringUtils;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

class BinaryFormatter {

    private void updateBlockMetadata(int bufferNum, int blockIndex, int[] lastDocIds, int[] docIdSizes, int[] freqSizes, int[] docIdBuffer,
                                     ByteBuffer docIdFreqByteBuffer, byte[] encodedDocIdGapByteBlock, byte[] encodedFreqByteBlock) {

        lastDocIds[blockIndex] = docIdBuffer[bufferNum - 1];
        docIdSizes[blockIndex] = encodedDocIdGapByteBlock.length;
        freqSizes[blockIndex] = encodedFreqByteBlock.length;
        docIdFreqByteBuffer.put(encodedDocIdGapByteBlock);
        docIdFreqByteBuffer.put(encodedFreqByteBlock);

    }

    private int[] formBlocks(String line, BufferedOutputStream invertedIndexBufferedOutputStream, BufferedOutputStream positionIndexBufferedOutputStream, String word) {

        String[] tupleStrs = StringUtils.split(line, ' ');


        int blockNum = (int) Math.ceil((tupleStrs.length / (double) 128));
        int[] docIdBuffer = new int[128];
        int[] freqBuffer = new int[128];
        int[] lastDocIds = new int[blockNum];
        int[] docIdSizes = new int[blockNum];
        int[] freqSizes = new int[blockNum];

        ArrayList<Integer> positionBuffer = new ArrayList<>();
        int[] positionSizes = new int[blockNum];

        int bufferNum = 0;
        int blockIndex = 0;
        int prevInt = 0;
        int firstDotComma;

        int positionSize = 0;

        ByteBuffer metadataArrayByteBuffer = ByteBuffer.allocate(blockNum * 3 * (Integer.SIZE / Byte.SIZE));
        ByteBuffer docIdFreqByteBuffer = ByteBuffer.allocate(tupleStrs.length * 2 * (Integer.SIZE / Byte.SIZE));

        ByteBuffer positionSizeArrayByteBuffer = ByteBuffer.allocate(blockNum * (Integer.SIZE / Byte.SIZE));
        ByteBuffer positionByteBuffer = ByteBuffer.allocate(line.length() * (Integer.SIZE / Byte.SIZE));


        for (String tupleStr : tupleStrs) {

            if (bufferNum == 128) {
                updateBlockMetadata(bufferNum, blockIndex, lastDocIds, docIdSizes, freqSizes, docIdBuffer, docIdFreqByteBuffer,
                        VarByte.VBEncodeNumberGaps(docIdBuffer, 0, bufferNum, prevInt), VarByte.VBEncodeNumbers(freqBuffer));

                positionSizes[blockIndex] = positionSize;
                prevInt = lastDocIds[blockIndex++];
                positionSize = 0;
                bufferNum = 0;
            }

            String[] docIDAndPositions = StringUtils.split(tupleStr, ',');
            docIdBuffer[bufferNum] = Integer.parseInt(docIDAndPositions[0]);
            freqBuffer[bufferNum] = docIDAndPositions.length - 1;

            for (int j = 1; j < docIDAndPositions.length; j++) {
                positionBuffer.add(Integer.parseInt(docIDAndPositions[j]));
            }

            byte[] encodedSingleDocPositions = VarByte.VBEncodeNumberGaps(positionBuffer);
            positionByteBuffer.put(encodedSingleDocPositions);
            positionSize += encodedSingleDocPositions.length;
            positionBuffer.clear();

            bufferNum++;
        }
        positionSizes[blockIndex] = positionSize;
        updateBlockMetadata(bufferNum, blockIndex, lastDocIds, docIdSizes, freqSizes, docIdBuffer, docIdFreqByteBuffer,
                VarByte.VBEncodeNumberGaps(docIdBuffer, 0, bufferNum, prevInt), VarByte.VBEncodeNumbers(freqBuffer, 0, bufferNum));


        for (int i = 0; i < blockNum; i++) metadataArrayByteBuffer.putInt(lastDocIds[i]);
        for (int i = 0; i < blockNum; i++) metadataArrayByteBuffer.putInt(docIdSizes[i]);
        for (int i = 0; i < blockNum; i++) metadataArrayByteBuffer.putInt(freqSizes[i]);
        for (int i = 0; i < blockNum; i++) positionSizeArrayByteBuffer.putInt(positionSizes[i]);


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

        return new int[]{tupleStrs.length, WriteToInvertedIndex(invertedIndexBufferedOutputStream, metadataArrayByteBuffer, docIdFreqByteBuffer),
                WriteToPositionIndex(positionIndexBufferedOutputStream, positionSizeArrayByteBuffer, positionByteBuffer)};
    }

    private int WriteToPositionIndex(BufferedOutputStream positionIndexBufferedOutputStream, ByteBuffer positionSizeArrayByteBuffer, ByteBuffer positionByteBuffer) {
        positionSizeArrayByteBuffer.flip();
        byte[] positionSizes = new byte[positionSizeArrayByteBuffer.limit()];
        positionSizeArrayByteBuffer.get(positionSizes);

        positionByteBuffer.flip();
        byte[] positions = new byte[positionByteBuffer.limit()];
        positionByteBuffer.get(positions);

        try {
            positionIndexBufferedOutputStream.write(positionSizes);
            positionIndexBufferedOutputStream.write(positions);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return positionSizes.length + positions.length;
    }

    private void WriteToLexicon(BufferedWriter lexiconBufferedWriter, String word, String wordOccurrence, String start, String end, String positionStart, String positionEnd) throws IOException {
        lexiconBufferedWriter.write(word);
        lexiconBufferedWriter.write(' ');
        lexiconBufferedWriter.write(wordOccurrence);
        lexiconBufferedWriter.write(' ');
        lexiconBufferedWriter.write(start);
        lexiconBufferedWriter.write(' ');
        lexiconBufferedWriter.write(end);
        lexiconBufferedWriter.write(' ');
        lexiconBufferedWriter.write(positionStart);
        lexiconBufferedWriter.write(' ');
        lexiconBufferedWriter.write(positionEnd);
        lexiconBufferedWriter.newLine();
    }

    private int WriteToInvertedIndex(BufferedOutputStream invertedIndexBufferedOutputStream, ByteBuffer metadataArrayByteBuffer, ByteBuffer docIdFreqByteBuffer) {

        metadataArrayByteBuffer.flip();
        byte[] metadata = new byte[metadataArrayByteBuffer.limit()];
        metadataArrayByteBuffer.get(metadata);

        docIdFreqByteBuffer.flip();
        byte[] docIdFreq = new byte[docIdFreqByteBuffer.limit()];
        docIdFreqByteBuffer.get(docIdFreq);

        try {
            invertedIndexBufferedOutputStream.write(metadata);
            invertedIndexBufferedOutputStream.write(docIdFreq);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return metadata.length + docIdFreq.length;
    }


    void generateInvertedIndex(String intermediatePostingPath, String outputPath) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(intermediatePostingPath));
            BufferedWriter lexiconBufferedWriter = new BufferedWriter(new FileWriter(outputPath.concat("Lexicon")));
            BufferedOutputStream invertedIndexBufferedOutputStream = new BufferedOutputStream(new FileOutputStream(outputPath.concat("InvertedIndex")));
            BufferedOutputStream positionIndexBufferedOutputStream = new BufferedOutputStream(new FileOutputStream(outputPath.concat("PositionIndex")));

            String line = bufferedReader.readLine();
            long start = 0L, end = 0L;
            long positionStart = 0L, positionEnd = 0L;
            int counter = 0;
            System.out.println("Start reformatting");
            while ((line = bufferedReader.readLine()) != null) {
                int firstSpace = line.indexOf(' ');
                String word = line.substring(0, firstSpace);
                int[] occurrenceAndListSize = formBlocks(line.substring(firstSpace + 1), invertedIndexBufferedOutputStream, positionIndexBufferedOutputStream, word);

                end = start + occurrenceAndListSize[1];
                positionEnd = positionStart + occurrenceAndListSize[2];
                WriteToLexicon(lexiconBufferedWriter, word, "" + occurrenceAndListSize[0], "" + start, "" + end, "" + positionStart, "" + positionEnd);
                start = end;
                positionStart = positionEnd;
            }
            lexiconBufferedWriter.close();
            invertedIndexBufferedOutputStream.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
