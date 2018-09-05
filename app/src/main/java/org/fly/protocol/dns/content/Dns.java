package org.fly.protocol.dns.content;

import org.fly.core.io.BufferUtils;
import org.fly.protocol.exception.PtrException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Dns {

    /*
     * See: https://en.wikipedia.org/wiki/List_of_DNS_record_types
     * 中文: https://zh.wikipedia.org/wiki/%E5%9F%9F%E5%90%8D%E4%BC%BA%E6%9C%8D%E5%99%A8%E8%A8%98%E9%8C%84%E9%A1%9E%E5%9E%8B%E5%88%97%E8%A1%A8
     */
    public enum TYPE {
        NONE(0),
        A(1),
        AAAA(28),
        AFSDB(18),
        APL(42),
        CAA(257),
        CDNSKEY(60),
        CDS(59),
        CERT(37),
        CNAME(5),
        DHCID(49),
        DLV(32769),
        DNAME(39),
        DNSKEY(48),
        DS(43),
        HIP(55),
        IPSECKEY(45),
        KEY(25),
        KX(36),
        LOC(29),
        MX(15),
        NAPTR(35),
        NS(2),
        NSEC(47),
        NSEC3(50),
        NSEC3PARAM(51),
        OPENPGPKEY(61),
        PTR(12),
        RRSIG(46),
        RP(17),
        SIG(24),
        SOA(6),
        SRV(33),
        SSHFP(44),
        TA(32768),
        TKEY(249),
        TLSA(52),
        TSIG(250),
        TXT(16),
        URI(256);

        protected final int value;

        TYPE(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static TYPE from(int value)
        {
            TYPE[] values = TYPE.values();
            for (int i = 0; i < values.length; i++) {
                if (values[i].getValue() == value)
                    return values[i];
            }

            return null;
        }
    }

    public enum OPCODE {
        QUERY(0),
        // inverse query
        STATUS(2),
        IQUERY(1);

        protected final int value;

        OPCODE(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static OPCODE from(int value)
        {
            OPCODE[] values = OPCODE.values();
            for (int i = 0; i < values.length; i++) {
                if (values[i].getValue() == value)
                    return values[i];
            }

            return null;
        }
    }

    public enum RCODE {
        OK(0),
        FORMAT_ERROR(1),
        SERVER_FAILURE(2),
        NAME_ERROR(3),
        NOT_IMPLEMENTED(4),
        REFUSED(5);

        protected final int value;

        RCODE(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static RCODE from(int value)
        {
            RCODE[] values = RCODE.values();
            for (int i = 0; i < values.length; i++) {
                if (values[i].getValue() == value)
                    return values[i];
            }

            return null;
        }
    }

	public final static int CLASS_INTERNET = 1;

    protected Header header = new Header();
    protected List<Query> questions = new ArrayList<>();
    protected List<Record> answers = new ArrayList<>();
    protected List<Record> authorities = new ArrayList<>();
    protected List<Record> additionals = new ArrayList<>();

    public Dns() {

    }

    public static Dns create(ByteBuffer byteBuffer) throws BufferUnderflowException, PtrException
    {
        Dns dns = new Dns();
        dns.read(byteBuffer);
        return dns;
    }

    public void read(ByteBuffer byteBuffer) throws BufferUnderflowException, PtrException
    {
        Label label = new Label(byteBuffer);

        header.read(byteBuffer);

        for (int i = 0; i < header.getQdCount(); i++) {
            Query query = new Query();
            query.read(byteBuffer, label);
            questions.add(query);
        }

        for (int i = 0; i < header.getAnCount(); i++) {
            Record record = new Record();
            record.read(byteBuffer, label);
            answers.add(record);
        }

        for (int i = 0; i < header.getNsCount(); i++) {
            Record record = new Record();
            record.read(byteBuffer, label);
            authorities.add(record);
        }

        for (int i = 0; i < header.getArCount(); i++) {
            Record record = new Record();
            record.read(byteBuffer, label);
            additionals.add(record);
        }

    }

    public void write(ByteBuffer outBuffer) throws BufferUnderflowException, PtrException
    {
        Label label = new Label(outBuffer);

        header.write(outBuffer);

        for (int i = 0; i < header.getQdCount(); i++) {
            Query query = questions.get(i);
            query.write(outBuffer, label);
        }

        for (int i = 0; i < header.getAnCount(); i++) {
            Record record = answers.get(i);
            record.write(outBuffer, label);
        }

        for (int i = 0; i < header.getNsCount(); i++) {
            Record record = authorities.get(i);
            record.write(outBuffer, label);
        }

        for (int i = 0; i < header.getArCount(); i++) {
            Record record = additionals.get(i);
            record.write(outBuffer, label);
        }
    }

    public Header getHeader() {
        return header;
    }

    public List<Query> getQuestions() {
        return questions;
    }

    public List<Record> getAnswers() {
        return answers;
    }

    public List<Record> getAuthorities() {
        return authorities;
    }

    public List<Record> getAdditionals() {
        return additionals;
    }

    public static long signedLongToUnsignedLong(long i)
    {
        return (i & 0x80000000L) != 0 ? i - 0xffffffff : i;
    }

    protected static int generateId()
    {
        return new Random().nextInt(65535);
    }

    public static class Header {

        private int id = 0;
        private int qr = 0;
        private OPCODE opcode = OPCODE.QUERY;
        private int aa = 0;
        private int tc = 0;
        private int rd = 0;
        private int ra = 0; //recursion answer
        private int z = 0;
        private RCODE rcode = RCODE.OK;
        private int qdCount = 0;
        private int anCount = 0;
        private int nsCount = 0;
        private int arCount = 0;

        public Header() {
        }

        public void read(ByteBuffer byteBuffer) throws BufferUnderflowException
        {
            if (byteBuffer.remaining() < 6 * Short.SIZE / Byte.SIZE)
                throw new BufferUnderflowException();

            id = BufferUtils.getUnsignedShort(byteBuffer);
            int fields = BufferUtils.getUnsignedShort(byteBuffer);
            qdCount = BufferUtils.getUnsignedShort(byteBuffer);
            anCount = BufferUtils.getUnsignedShort(byteBuffer);
            nsCount = BufferUtils.getUnsignedShort(byteBuffer);
            arCount = BufferUtils.getUnsignedShort(byteBuffer);

            rcode = RCODE.from(fields & 0xf); //1111
            z = (fields >> 4) & 0x7; // 111
            ra =  (fields >> 7) & 1;
            rd =  (fields >> 8) & 1;
            tc = (fields >> 9) & 1;
            aa =  (fields >> 10) & 1;
            opcode =  OPCODE.from((fields >> 11) & 0xf);
            qr =  (fields >> 15) & 1;
        }

        public void write(ByteBuffer outBuffer) throws BufferUnderflowException
        {
            if (outBuffer.remaining() < 6 * Short.SIZE / Byte.SIZE)
                throw new BufferUnderflowException();

            BufferUtils.putUnsignedShort(outBuffer, id);

            int flags = 0;
            flags =  (flags << 1) | qr;
            flags =  (flags << 4) | opcode.getValue();
            flags =  (flags << 1) | aa;
            flags =  (flags << 1) | tc;
            flags =  (flags << 1) | rd;
            flags =  (flags << 1) | ra;
            flags =  (flags << 3) | z;
            flags =  (flags << 4) | rcode.getValue();

            BufferUtils.putUnsignedShort(outBuffer, flags);
            BufferUtils.putUnsignedShort(outBuffer, qdCount);
            BufferUtils.putUnsignedShort(outBuffer, anCount);
            BufferUtils.putUnsignedShort(outBuffer, nsCount);
            BufferUtils.putUnsignedShort(outBuffer, arCount);

        }

        public boolean isQuery()
        {
            return 0 == qr;
        }

        public boolean isResponse()
        {
            return 1 == qr;
        }

        public boolean isQueryDomain()
        {
            return OPCODE.QUERY == opcode;
        }

        public boolean isQueryStatus()
        {
            return OPCODE.STATUS == opcode;
        }

        public boolean isInverseQuery()
        {
            return OPCODE.IQUERY == opcode;
        }

        public boolean isTruncated()
        {
            return 1 == tc;
        }

        public int getId() {
            return id;
        }

        public int getQr() {
            return qr;
        }

        public OPCODE getOpcode() {
            return opcode;
        }

        public int getTc() {
            return tc;
        }

        public int getRd() {
            return rd;
        }

        public int getRa() {
            return ra;
        }

        public int getZ() {
            return z;
        }

        public RCODE getRcode() {
            return rcode;
        }

        public int getQdCount() {
            return qdCount;
        }

        public int getAnCount() {
            return anCount;
        }

        public int getNsCount() {
            return nsCount;
        }

        public int getArCount() {
            return arCount;
        }

        public void setId(int id) {
            this.id = id;
        }

        public void setQr(int qr) {
            this.qr = qr;
        }

        public void setOpcode(OPCODE opcode) {
            this.opcode = opcode;
        }

        public void setAa(int aa) {
            this.aa = aa;
        }

        public void setTc(int tc) {
            this.tc = tc;
        }

        public void setRd(int rd) {
            this.rd = rd;
        }

        public void setRa(int ra) {
            this.ra = ra;
        }

        public void setZ(int z) {
            this.z = z;
        }

        public void setRcode(RCODE rcode) {
            this.rcode = rcode;
        }

        public void setQdCount(int qdCount) {
            this.qdCount = qdCount;
        }

        public void setAnCount(int anCount) {
            this.anCount = anCount;
        }

        public void setNsCount(int nsCount) {
            this.nsCount = nsCount;
        }

        public void setArCount(int arCount) {
            this.arCount = arCount;
        }
    }

    public static class Query {

        private String name = "";
        private TYPE type = TYPE.A;
        private int clazz = CLASS_INTERNET;

        private final static List<TYPE> commonTypes = Arrays.asList(
                TYPE.A, TYPE.AAAA, TYPE.CNAME, TYPE.NS, TYPE.TXT, TYPE.MX
        );

        public Query(String name, TYPE type) {
            this.name = name;
            this.type = type;
        }

        public Query() {
        }

        public void read(ByteBuffer byteBuffer, Label label) throws BufferUnderflowException, PtrException
        {
            if (byteBuffer.remaining() < 1 + 2 * Short.SIZE / Byte.SIZE)
                throw new BufferUnderflowException();

            name = Label.join(label.readLabels());
            type = TYPE.from(BufferUtils.getUnsignedShort(byteBuffer));
            clazz = BufferUtils.getUnsignedShort(byteBuffer);
        }

        public void write(ByteBuffer outBuffer, Label label) throws BufferUnderflowException, PtrException
        {
            if (outBuffer.remaining() < 1 + 2 * Short.SIZE / Byte.SIZE)
                throw new BufferUnderflowException();

            label.writeLabels(name);
            BufferUtils.putUnsignedShort(outBuffer, type.getValue());
            BufferUtils.putUnsignedShort(outBuffer, clazz);
        }

        public boolean isA()
        {
            return TYPE.A == type;
        }

        public boolean isAAA()
        {
            return TYPE.AAAA == type;
        }

        public boolean isNS()
        {
            return TYPE.NS == type;
        }

        public boolean isCNAME()
        {
            return TYPE.CNAME == type;
        }

        public boolean isMX()
        {
            return TYPE.MX == type;
        }

        public boolean isTXT()
        {
            return TYPE.TXT == type;
        }

        public boolean isCommonType()
        {
            return commonTypes.contains(type);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public TYPE getType() {
            return type;
        }

        public void setType(TYPE type) {
            this.type = type;
        }

        public int getClazz() {
            return clazz;
        }

        public void setClazz(int clazz) {
            this.clazz = clazz;
        }

    }

    public static class Record {

        protected String name = "";
        protected TYPE type = TYPE.A;
        protected int clazz = CLASS_INTERNET;
        protected long ttl = 600;
        protected String data = "";

        public Record(String name, TYPE type, int clazz, int ttl, String data) {
            this.name = name;
            this.type = type;
            this.clazz = clazz;
            this.ttl = ttl;
            this.data = data;
        }

        public Record() {
        }

        public Record(String domain, TYPE type, String value, int ttl) {
            name = domain;
            this.type = type;
            data = value;
            this.ttl = ttl;
        }

        public void read(ByteBuffer byteBuffer, Label label) throws BufferUnderflowException, PtrException
        {
            if (byteBuffer.remaining() < 1 + 3 * Short.SIZE / Byte.SIZE + Integer.SIZE / Byte.SIZE)
                throw new BufferUnderflowException();

            name = Label.join(label.readLabels());
            type = TYPE.from(BufferUtils.getUnsignedShort(byteBuffer));
            clazz = BufferUtils.getUnsignedShort(byteBuffer);
            ttl = BufferUtils.getUnsignedInt(byteBuffer);
            //ttl = signedLongToUnsignedLong(ttl);

            int rdLength = BufferUtils.getUnsignedShort(byteBuffer);

            if (type == TYPE.A || type == TYPE.AAAA)
            {
                byte[] bytes = new byte[rdLength];
                byteBuffer.get(bytes, 0, rdLength);
                try {
                    data = InetAddress.getByAddress(bytes).getHostAddress();
                } catch (UnknownHostException e)
                {
                    data = "";
                }
            } else {
                data = Label.join(label.readLabels());
            }

        }

        public void write(ByteBuffer outBuffer, Label label) throws BufferUnderflowException, PtrException
        {
            if (outBuffer.remaining() < 1 + 3 * Short.SIZE / Byte.SIZE + Integer.SIZE / Byte.SIZE)
                throw new BufferUnderflowException();

            label.writeLabels(name);
            BufferUtils.putUnsignedShort(outBuffer, type.getValue());
            BufferUtils.putUnsignedShort(outBuffer, clazz);
            BufferUtils.putUnsignedInt(outBuffer, ttl);

            // the true length that , write 0
            int lengthPosition = outBuffer.position();
            BufferUtils.putUnsignedShort(outBuffer,0);

            if (type == TYPE.A || type == TYPE.AAAA)
            {
                try {

                    InetAddress inetAddress = InetAddress.getByName(data);
                    outBuffer.put(inetAddress.getAddress());
                } catch (UnknownHostException e)
                {

                }
            } else {
                label.writeLabels(data);
            }

            int length = outBuffer.position() - lengthPosition - Short.SIZE / Byte.SIZE;

            outBuffer.position(lengthPosition);
            BufferUtils.putUnsignedShort(outBuffer,length);

            outBuffer.position(lengthPosition + length + Short.SIZE / Byte.SIZE);

        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public TYPE getType() {
            return type;
        }

        public void setType(TYPE type) {
            this.type = type;
        }

        public int getClazz() {
            return clazz;
        }

        public void setClazz(int clazz) {
            this.clazz = clazz;
        }

        public long getTtl() {
            return ttl;
        }

        public void setTtl(long ttl) {
            this.ttl = ttl;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }


}
