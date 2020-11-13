import com.google.common.io.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import java.io.*;
import java.util.*;

class TrecParser {
    private int bufferSize;

    TrecParser(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    private void sortInvertedIndex(String[][] invertedIndex, int fromIndex, int toIndex, Comparator<String[]>lexOrder, Comparator<String[]> lenOrder, Comparator<String[]>numOrder) {
        Arrays.parallelSort(invertedIndex, fromIndex, toIndex, lexOrder.thenComparing(lenOrder).thenComparing(numOrder));
    }

    private void updateFreqMap(Map<String, MutableInt> freqMap, String word) {
        MutableInt count = freqMap.get(word);
        if (count == null) freqMap.put(word, new MutableInt());
        else count.increment();
    }


    private void generateInitialIntermediatePostingsASCII(String[][] invertedIndex, int fileCount, int wordCount, String outputFilePath) throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputFilePath.concat("IP".concat("" + fileCount).concat("_0")))));
        for (int i = 0; i < wordCount; i++) {
            bw.write(invertedIndex[i][0]);
            bw.write(" ");
            bw.write(invertedIndex[i][1]);
            bw.write(",");
            bw.write(invertedIndex[i][2]);
            bw.newLine();
            invertedIndex[i] = null;
        }
        bw.close();
    }

    private void writeToIntermediatePageTableASCII(BufferedWriter pageTableBufferedWriter, String URL, String docLen, String startPosition, String endPosition) throws IOException {
        pageTableBufferedWriter.write(URL); pageTableBufferedWriter.write(' ');
        pageTableBufferedWriter.write(docLen); pageTableBufferedWriter.write(' ');
        pageTableBufferedWriter.write(startPosition); pageTableBufferedWriter.write(' ');
        pageTableBufferedWriter.write(endPosition);pageTableBufferedWriter.newLine();
    }

    private ArrayList<Long> getPagePositions(String sourceFilePath) throws IOException{

        CountingInputStream countingInputStream = new CountingInputStream(new FileInputStream(sourceFilePath));

        byte [] buffer = new byte[1024 * 16];
        ArrayList <Long> pagePositions = new ArrayList<>(3300000);
        int lf = '\n';
        int len;
        long pos = 0;

        StringBuilder stringBuilder = new StringBuilder();
        while ((len = countingInputStream.read(buffer)) != -1) {
            if(len < buffer.length) {break;}
            //if(pagePositions.size() > 100000) {break;}
            for (int i = 0; i< buffer.length; i++) {
                if (buffer[i] == lf) {
                    if (stringBuilder.toString().equals("</TEXT>")){pagePositions.add(pos + i);}
                    stringBuilder.setLength(0);
                } else {
                    stringBuilder.append((char)buffer[i]);
                }
            }
            pos = countingInputStream.getCount();
        }

        for (int i = 0; i< len; i++) {
            if (buffer[i] == lf) {
                if (stringBuilder.toString().equals("</TEXT>")){pagePositions.add(pos + i);}
                stringBuilder.setLength(0);
            } else {
                stringBuilder.append((char)buffer[i]);
            }
        }

        countingInputStream.close();
        return pagePositions;
    }


    int parseAndSortPhase(String sourceFilePath, String outputPath) {
        String[][] invertedIndex = new String[bufferSize][3];
        Map<String, MutableInt> freqMap = new TreeMap<>();
        Comparator<String[]> lexOrder = Comparator.comparing(row -> row[0]);
        Comparator<String[]> lenOrder = Comparator.comparing(row-> row[1].length());
        Comparator<String[]> numOrder = Comparator.comparing(row -> row[1]);


        int fileNum = 0, docNum = 0, wordCount = 0;
        long pageStartPosition = 0;
        String line;

        try {
            ArrayList<Long> pagePositions = getPagePositions(sourceFilePath);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(sourceFilePath));
            BufferedWriter pageTableBufferedWriter = new BufferedWriter(new FileWriter(outputPath.concat("PageTable")));


            System.out.println("Parsing docs");
            long begin = System.currentTimeMillis();
            while ((line = bufferedReader.readLine()) != null) {
                if (line.equals("<TEXT>")) {
                    String docNumStr = "" + docNum;
                    String URL = bufferedReader.readLine();

                    int docLen = 0;

                    while (!(line = bufferedReader.readLine()).equals("</TEXT>")) {
                        String [] words = StringUtils.split(line, " \"?.,:()“”;!~'|#{}[]$‘’*-+%&—–_/");
                        for (String word : words) {
                            if(word.length() > 30) continue;
                            docLen++;
                            updateFreqMap(freqMap, word.toLowerCase());
                        }
                    }
                    for (Map.Entry<String, MutableInt> entry : freqMap.entrySet()) {

                        if (wordCount >= bufferSize) {
                            long end = System.currentTimeMillis();
                            System.out.println("Elapsed Time: "+(end - begin));

                            System.out.println("start sort");
                            long begin0 = System.currentTimeMillis();
                            sortInvertedIndex(invertedIndex, 0, bufferSize,lexOrder, lenOrder, numOrder);
                            long end0 = System.currentTimeMillis();
                            System.out.println(String.format("Sort Time: %d",(end0 - begin0)));

                            System.out.println("generate intermediate postings");
                            long begin1 = System.currentTimeMillis();
                            generateInitialIntermediatePostingsASCII(invertedIndex, ++fileNum, bufferSize, outputPath);
                            long end1 = System.currentTimeMillis();
                            System.out.println(String.format("Write Time: %d",(end1 - begin1)));

                            wordCount = 0;

//                            if(fileNum == 256) {
//                                pageTableBufferedWriter.close();
//                                return 256;
//                            }
                        }
                        invertedIndex[wordCount++] = new String[]{entry.getKey(), docNumStr, "" + entry.getValue().get()};
                    }

                    writeToIntermediatePageTableASCII(pageTableBufferedWriter, URL, "" + docLen, "" + pageStartPosition, "" + pagePositions.get(docNum));
                    pageStartPosition = pagePositions.get(docNum++);
                    freqMap.clear();
                    if (docNum % 10000 == 0) System.out.println(docNum);
                }
            }
            sortInvertedIndex(invertedIndex, 0, wordCount,lexOrder, lenOrder, numOrder);
            generateInitialIntermediatePostingsASCII(invertedIndex, fileNum, wordCount, outputPath);
            pageTableBufferedWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileNum;
    }
}
