package org.fly.protocol.dns.content;

import org.fly.android.localvpn.structs.BufferUtils;
import org.fly.protocol.exception.PtrException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Label {

    public final static String DOT = ".";

    ByteBuffer byteBuffer;
    Map<String, Integer> labelPositions = new HashMap<>();

    public Label(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public List<String> readLabels() throws PtrException
    {
       return readLabels(-1);
    }

    public List<String> readLabels(int offset) throws PtrException
    {
        if (offset != -1)
            byteBuffer.position(offset);

        List<String> labels = new ArrayList<>();

        while(true)
        {
            byte ch = byteBuffer.get();
            if (isEndOfLabel(ch))
            {
                if (!labels.isEmpty())
                {
                    // insert to labelPositions
                    findLabelsPtr(labels, byteBuffer.position() - 1 - join(labels).length());
                }
                break;
            }
            else if (isPtr(ch))
            {
                byteBuffer.position(byteBuffer.position() - 1);
                int ptr = BufferUtils.getUnsignedShort(byteBuffer);
                ptr = getPtrOffset(ptr);

                byteBuffer.mark();

                labels = readLabels(ptr);

                byteBuffer.reset();
                break;
            } else {
                int len = ch & 0xff;
                byte[] bytes = new byte[len];
                byteBuffer.get(bytes, 0, len);
                labels.add(new String(bytes));
            }
        }
        return labels;
    }

    public void writeLabels(List<String> labels, int offset) throws PtrException
    {
        if (offset != -1)
            byteBuffer.position(offset);

        int ptr = findLabelsPtr(labels, byteBuffer.position());

        if (ptr == -1) {
            for (String label: labels
                 ) {
                BufferUtils.putUnsignedByte(byteBuffer, label.length());
                byteBuffer.put(label.getBytes());
            }
            byteBuffer.put((byte)0);

        } else {
            BufferUtils.putUnsignedShort(byteBuffer, ptr);
        }
    }

    public void writeLabels(String domain, int offset) throws PtrException
    {
        writeLabels(Arrays.asList(domain.split("\\" + DOT)), offset);
    }

    public void writeLabels(String domain) throws PtrException
    {
        writeLabels(domain, -1);
    }

    public void writeLabels(List<String> labels) throws PtrException
    {
        writeLabels(labels, -1);
    }

    protected int findLabelsPtr(List<String> labels, int currentOffset) throws PtrException
    {
        String key = join(labels);

        if (labelPositions.containsKey(key))
        {
            return getOffsetPtr(labelPositions.get(key));
        } else {
            labelPositions.put(key, currentOffset);
        }

        return -1;
    }

    protected boolean isEndOfLabel(byte ch)
    {
        return ch == '\0';
    }

    protected static boolean isPtr(byte ch){
        return (ch & 0xc0) == 0xc0; // 1100 0000
    }

    public static int getPtrOffset(int ptr)
    {
        return ptr & 0x3fff; // 0011 1111 1111 1111
    }

    public static int getOffsetPtr(int offset) throws PtrException
    {
        if (offset > 0x3fff) { // 0011 1111 1111 1111
            throw new PtrException("Ptr offset must <= 0x3fff.");
        }

        return offset | 0xc000; //1100 0000 0000 0000
    }

    public static String join(CharSequence separator, List<String> list)
    {
        StringBuilder s = new StringBuilder();
        for(String r : list){
            if (s.length() != 0)
                s.append(separator);
            s.append(r);
        }
        return s.toString();
    }

    public static String join(List<String> list)
    {
        return join(DOT, list);
    }

}
