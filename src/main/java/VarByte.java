import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class VarByte {
    static int VBEncodeBufferSize(int num) {
        if (num == 0) return 1;
        if(num < 0) {
            System.out.println("Negative number. Please Check DocID or the order of DocID.");
            System.exit(1);
        }
        int ones = 32 - Integer.numberOfLeadingZeros(num);
        return (ones + 6) / 7;
    }

    static byte[] VBEncodeNumber(int num) {
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

    static byte[] VBEncodeNumbers(int [] nums) {

        ByteBuffer buffer = ByteBuffer.allocate(nums.length * (Integer.SIZE / Byte.SIZE));
        for (int num : nums){ buffer.put(VBEncodeNumber(num));}
        buffer.flip();
        byte[] vb  = new byte[buffer.limit()];
        buffer.get(vb);
        return vb;
    }

    static byte[] VBEncodeNumbers(int [] nums, int start, int end) {

        ByteBuffer buffer = ByteBuffer.allocate(nums.length * (Integer.SIZE / Byte.SIZE));
        for (int i = start; i < end; i++){
            buffer.put(VBEncodeNumber(nums[i]));
        }
        buffer.flip();
        byte[] vb  = new byte[buffer.limit()];
        buffer.get(vb);
        return vb;
    }

    static byte[] VBEncodeNumberGaps(int [] nums) {
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

    static byte[] VBEncodeNumberGaps(ArrayList<Integer> nums) {

        ByteBuffer buffer = ByteBuffer.allocate(nums.size() * (Integer.SIZE / Byte.SIZE));

        buffer.put(VBEncodeNumber(nums.get(0)));

        int bufferIndex = 1;
        while (bufferIndex < nums.size()) {
            //System.out.println(nums[bufferIndex]);
            buffer.put(VBEncodeNumber(nums.get(bufferIndex) - nums.get(bufferIndex - 1)));
            bufferIndex++;
        }
        buffer.flip();
        byte[] vb  = new byte[buffer.limit()];
        buffer.get(vb);
        return vb;
    }


    static byte[] VBEncodeNumberGaps(int [] nums, int start, int end, int prevInt) {
        ByteBuffer buffer = ByteBuffer.allocate( (end - start) * (Integer.SIZE / Byte.SIZE));
//        System.out.println("prevint: "+ prevInt);

        buffer.put(VBEncodeNumber(nums[start] - prevInt));

        int bufferIndex = start + 1;
        while (bufferIndex < end) {
            //System.out.println(nums[bufferIndex]);
            buffer.put(VBEncodeNumber(nums[bufferIndex] - nums[bufferIndex - 1]));
            bufferIndex++;
        }
        buffer.flip();
        byte[] vb  = new byte[buffer.limit()];
        buffer.get(vb);
        return vb;
    }

    static int [] VBDecode(byte [] byteStream) {
        List<Integer> nums = new ArrayList<>();
        int num = 0;
        int offset = 0;
        int payload;
        for (byte b : byteStream) {
            if (((payload = b & 0xff) & 0x80) != 0) {
                num |= (payload & 0x7f) << offset;
                offset += 7;
            } else {
                nums.add((num | (payload << offset)));
                num = 0;
                offset = 0;
            }
        }
        return Ints.toArray(nums);
    }

    static int [] VBDecode(byte [] byteStream, int limit) {
        List<Integer> nums = new ArrayList<>();
        int num = 0;
        int offset = 0;
        int payload;
        for (byte b : byteStream) {
            if (((payload = b & 0xff) & 0x80) != 0) {
                num |= (payload & 0x7f) << offset;
                offset += 7;
            } else {
                nums.add((num | (payload << offset)));
                num = 0;
                offset = 0;
            }
        }
        return Ints.toArray(nums);
    }


    static void printByteArray(byte [] buffer) {
        System.out.println("Buffer Len:" + buffer.length);
        for (byte j : buffer) {
            String s1 = String.format("%8s", Integer.toBinaryString(j & 0xFF)).replace(' ', '0');
            System.out.println(s1);
        }
        System.out.println();
    }

    static int [] restoreList(int [] list) {
        if (list.length == 1) return list;

        int [] restoredList = Arrays.copyOf(list, list.length);
        int accumulator = restoredList[0];
        for(int i = 1; i < restoredList.length; i++){
            accumulator += restoredList[i];
            restoredList[i] = accumulator;
        }
        return restoredList;
    }

    static int [] restoreList(int [] list, int prevInt) {
        int [] restoredList = Arrays.copyOf(list, list.length);
        restoredList[0] += prevInt;
        if (restoredList.length == 1) return restoredList;
        int accumulator = restoredList[0];
        for(int i = 1; i < restoredList.length; i++){
            accumulator += restoredList[i];
            restoredList[i] = accumulator;
        }
        return restoredList;
    }

}
