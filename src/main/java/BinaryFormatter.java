import org.apache.commons.lang3.StringUtils;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


class BinaryFormatter {

    private int VBEncodeBufferSize(int num) {
        if (num == 0) return 1;
        if(num < 0) {
            System.out.println("Negative number. Please Check DocID or the order of DocID.");
            System.exit(1);
        }
        int ones = 32 - Integer.numberOfLeadingZeros(num);
        return (ones + 6) / 7;
    }

    private byte[] VBEncodeNumber(int num) {
        int bufferSize = VBEncodeBufferSize(num);
        byte [] vb = new byte[bufferSize];
        int bufferIndex = 0;
        while((num & 0xffffff80) != 0) {
            vb[bufferIndex++] = (byte) ((num & 0x7f) | 0x80);
            num >>>= 7;
        }
        vb[bufferIndex] = (byte) (num & 0x7f);
        return vb;
    }

    private byte[] VBEncodeNumbers(int [] nums) {

        ByteBuffer buffer = ByteBuffer.allocate(nums.length * (Integer.SIZE / Byte.SIZE));
        for (int num : nums){ buffer.put(VBEncodeNumber(num));}
        buffer.flip();
        byte[] vb  = new byte[buffer.limit()];
        buffer.get(vb);
        return vb;
    }

    private byte[] VBEncodeNumberGaps(int [] nums) {
        ByteBuffer buffer = ByteBuffer.allocate(nums.length * (Integer.SIZE / Byte.SIZE));

        buffer.put(VBEncodeNumber(nums[0]));

        int bufferIndex = 1;
        while (bufferIndex < nums.length) {
            //System.out.println(nums[bufferIndex]);
            buffer.put(VBEncodeNumber(nums[bufferIndex] - nums[bufferIndex - 1]));
            bufferIndex++;
        }
        buffer.flip();
        byte[] vb  = new byte[buffer.limit()];
        buffer.get(vb);
        return vb;
    }

    List<Integer> VBDecode(byte [] byteStream) {
        List<Integer> nums = new ArrayList<>();
        int num = 0;
        int offset = 0;
        int payload;
        for (int bufferIndex = 0; bufferIndex < byteStream.length; bufferIndex++) {
            if(((payload = byteStream[bufferIndex] & 0xff) & 0x80) != 0) {
                num |= (payload & 0x7f) << offset;
                offset += 7;
            } else {
                nums.add((num | (payload << offset)));
                num = 0;
                offset = 0;
            }
        }
        return nums;
    }

    void printByteArray(byte [] buffer) {
        System.out.println("Buffer Len:" + buffer.length);
        for (byte j : buffer) {
            String s1 = String.format("%8s", Integer.toBinaryString(j & 0xFF)).replace(' ', '0');
            System.out.println(s1);
        }
        System.out.println();
    }
    void printList(List<Integer> list){
        System.out.println(Arrays.toString(list.toArray(new Integer[0])));
    }

    void printOriginalList (List<Integer> list) {
        int total = list.get(0);
        System.out.print(total);
        if(list.size() == 1) {
            System.out.println();
            return;
        }
        for(int i = 1; i < list.size() ;i++) {
            System.out.print(' ');
            System.out.print((total += list.get(i)));
        }
        System.out.println();
    }

    private int [][] getDocIdsAndFreqs(String line) {
        String [] tupleStrs = StringUtils.split(line, ' ');
        int [][] docIdFreqList = new int [2][tupleStrs.length];
        for (int i = 0; i < tupleStrs.length; i++) {
            int firstDotComma = tupleStrs[i].indexOf(',');
            docIdFreqList[0][i] = Integer.parseInt(tupleStrs[i].substring(0,firstDotComma));
            docIdFreqList[1][i] = Integer.parseInt(tupleStrs[i].substring(firstDotComma+1));
        }
        return docIdFreqList;
    }

    void WriteToLexicon(BufferedWriter lexbw, String wordOccurrence, String word, String start, String middle, String end) throws IOException {
        lexbw.write(wordOccurrence);lexbw.write(' ');
        lexbw.write(word);lexbw.write(' ');
        lexbw.write(start);lexbw.write(' ');
        lexbw.write(middle);lexbw.write(' ');
        lexbw.write(end); lexbw.newLine();
    }

    void WriteToInvertedIndex(BufferedOutputStream bos, byte [] encodedDocIdGapBytes, byte [] encodedFreqBytes) throws IOException{
        bos.write(encodedDocIdGapBytes);
        bos.write(encodedFreqBytes);
    }


    public void generateInvertedIndex(String intermediatePostingPath, String outputPath) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(intermediatePostingPath));
            BufferedWriter lexbw = new BufferedWriter(new FileWriter(outputPath.concat("Lexicon")));
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath.concat("InvertedIndex")));

            String line = br.readLine();
            int start = 0, middle = 0, end = 0;
            int counter = 0;
            System.out.println("Start reformatting");
            while((line = br.readLine()) != null) {
                int firstSpace = line.indexOf(' ');
                String word = line.substring(0,firstSpace);
                int [][] docIdFreqList = getDocIdsAndFreqs(line.substring(firstSpace + 1));
                byte [] encodedDocIdGapBytes = VBEncodeNumberGaps(docIdFreqList[0]);
                byte [] encodedFreqBytes = VBEncodeNumbers(docIdFreqList[1]);
                WriteToInvertedIndex(bos, encodedDocIdGapBytes, encodedFreqBytes);
                middle = start + encodedDocIdGapBytes.length;
                end = middle + encodedFreqBytes.length;
                WriteToLexicon(lexbw, word, ""+docIdFreqList[0].length, ""+start, ""+middle, ""+end);
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
