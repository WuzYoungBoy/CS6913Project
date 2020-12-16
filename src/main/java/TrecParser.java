import com.google.common.io.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import java.io.*;
import java.util.*;

class TrecParser {
    private int bufferSize;

    TrecParser(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    private void sortInvertedIndex(Posting [] invertedIndex, int fromIndex, int toIndex, Comparator<Posting>lexOrder, Comparator<Posting> lenOrder, Comparator<Posting>numOrder) {
        Arrays.parallelSort(invertedIndex, fromIndex, toIndex, lexOrder.thenComparing(lenOrder).thenComparing(numOrder));
    }

    private void updatePositionMap(Map<String, ArrayList<String>> positionMap, String word, int position) {
        ArrayList<String> positions = positionMap.get(word);
        if (positions == null) positionMap.put(word, new ArrayList<String>(){{add(""+position);}} );
        else positions.add(""+position);
    }

    private void generateInitialIntermediatePostingsASCII(Posting [] invertedIndex, int fileCount, int wordCount, String outputFilePath) throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(outputFilePath.concat("IP".concat("" + fileCount).concat("_0"))));
        for (int i = 0; i < wordCount; i++) {
            bw.write(invertedIndex[i].getWord());
            bw.write(" ");
            bw.write(invertedIndex[i].getDocNumStr());
            bw.write(",");
            bw.write(String.join(",",invertedIndex[i].getPositions()));
            bw.newLine();
            invertedIndex[i] = null;
        }
        bw.close();
    }

    private void writeToIntermediatePageTableASCII(BufferedWriter pageTableBufferedWriter, String URL, String docNo, String docLen) throws IOException {
        pageTableBufferedWriter.write(URL); pageTableBufferedWriter.write(' ');
        pageTableBufferedWriter.write(docNo); pageTableBufferedWriter.write(' ');
        pageTableBufferedWriter.write(docLen);pageTableBufferedWriter.newLine();
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

        Posting [] invertedIndex = new Posting[bufferSize];

        Map<String, ArrayList<String>> positionMap = new TreeMap<>();
        Comparator<Posting> lexOrder = Comparator.comparing(posting -> posting.getWord());
        Comparator<Posting> lenOrder = Comparator.comparing(posting-> posting.getDocNumStr().length());
        Comparator<Posting> numOrder = Comparator.comparing(posting -> posting.getDocNumStr());

        int fileNum = 0, docNum = 0, wordCount = 0;
        long pageStartPosition = 0, totalDocLen = 0;
        String line, docNo = "";

        try {
//            ArrayList<Long> pagePositions = getPagePositions(sourceFilePath);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(sourceFilePath));
            BufferedWriter pageTableBufferedWriter = new BufferedWriter(new FileWriter(outputPath.concat("PageTable")));


            System.out.println("Parsing docs");
            long begin = System.currentTimeMillis();
            while ((line = bufferedReader.readLine()) != null) {
                if(line.equals("<DOC>")) {
                    docNo = StringUtils.split(bufferedReader.readLine(), "><")[1];
                }
                if (line.equals("<TEXT>")) {
                    String docNumStr = "" + docNum;
                    String URL = bufferedReader.readLine();

                    int docLen = 0;

                    while (!(line = bufferedReader.readLine()).equals("</TEXT>")) {
                        String [] words = StringUtils.split(line, " \"?.,:()“”;!~'|#{}[]$‘’*-+%&—–_/");

                        for(int i = 0; i < words.length; i++) {
                            if(words[i].length() > 30) continue;
                            updatePositionMap(positionMap, words[i].toLowerCase(), docLen + i);
                        }

                        docLen+= words.length;
                    }
                    for (Map.Entry<String, ArrayList<String>> entry : positionMap.entrySet()) {

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

                        invertedIndex[wordCount++] = new Posting(entry.getKey(),docNumStr,entry.getValue());
                    }

                    writeToIntermediatePageTableASCII(pageTableBufferedWriter, URL, docNo, "" + docLen);
                    totalDocLen += docLen;
                    docNum++;
                    positionMap.clear();
                    if (docNum % 10000 == 0) System.out.println(docNum);
                }
            }
            sortInvertedIndex(invertedIndex, 0, wordCount,lexOrder, lenOrder, numOrder);
            generateInitialIntermediatePostingsASCII(invertedIndex, fileNum, wordCount, outputPath);

            pageTableBufferedWriter.newLine();
            pageTableBufferedWriter.write("" + totalDocLen);pageTableBufferedWriter.newLine();
            pageTableBufferedWriter.write("" + docNum);
            pageTableBufferedWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileNum;
    }
}
