package d2d.testing.net.threads.workers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DataFormat {
    public static final byte[] START_PACKET_CONST = {0x11,0x17,0x16,0x15};
    public static final byte[] END_PREFIX_CONST = {0x11,0x15,0x16,0x17};

    public static final int LENGTH_HEADER = 5;

    public static final int TYPE_POSITION = 4;

    public static final byte TYPE_MSG = 0x15;
    public static final byte TYPE_IMAGE = 0x16;
    public static final byte TYPE_FILE = 0x17;

    public static byte[] createMessagePacket(String message){
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] data = message.getBytes();
        try {
            //output.write(START_PACKET_CONST);     //ALWAYS PREFIX
            output.write(TYPE_MSG);         //SET TYPE

            //TODO: HASH Y CHECKING AL RECIBIR, TIMESTAMP, QUIEN LO HA ENVIADO, NETWORK JUMPTRACE PARA SABER POR QUIEN HA PASADO?
            output.write(intToByte(data.length));
            output.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return output.toByteArray();
    }

    public static byte[] createFilePacket(byte[] file){
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            //output.write(START_PACKET_CONST);     //ALWAYS PREFIX
            output.write(TYPE_FILE);                //SET TYPE

            //TODO: HASH Y CHECKING AL RECIBIR, TIMESTAMP, QUIEN LO HA ENVIADO, NETWORK JUMPTRACE PARA SABER POR QUIEN HA PASADO?
            output.write(intToByte(file.length));
            output.write(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return output.toByteArray();
    }

    public static List<DataPacket> checkPacket(byte[] data)
    {
        List<DataPacket> out = new LinkedList<>();
        DataPacket packet = new DataPacket(data);
        out.add(packet);
        if (data.length < 4)
            return out; //!error

        packet.setLength(byteToInt(Arrays.copyOfRange(data, 0, 3)));

        if(data.length < 5)        //CADA TIPO DE MENSAJE QUE PODEMOS ENVIAR
            return out; //!error

        switch (data[TYPE_POSITION]){ //cambiar porque NO ESTE EN EL RANGO DE NUESTROS TIPOS DE MENSAJES.. O VAMOS A TENER QUE HACER ALGO MAS AHI SEPARADO? CABECERAS PROPIAS... ETC
            case TYPE_MSG:
                packet.setType(TYPE_MSG);
                break;
            case TYPE_IMAGE:
                packet.setType(TYPE_MSG);
                break;
            case TYPE_FILE:
                packet.setType(TYPE_FILE);
                break;
            default:
                //ERROR NO HAY TIPO DE MENSAJE!!
        }

        if(data.length - LENGTH_HEADER < length)
        {
            //EL MENSAJE NO ESTA COMPLETO HAY QUE ALMACENAR EL TIPO DE MENSAJE Y LA LONGITUD JUNTO CON LOS DATOS QUE YA TENEMOS
            //DEVOLVEMOS UN PAQUETE CON ALGUN FLAG DE INCOMPLETO
            packet.setCompleted(false);
            return out;
            //todo futuro: archivos muy grandes se nos peta la memoria o como va la cosa?
        }
        else
        {
            //TENEMOS EL MENSAJE COMPLETO
            //JUNTAMOS LOS DATOS Y DEVOLVEMOS PAQUETE CON FLAG DE COMPLETO, EL WORKER LO PUEDE PROCESAR
            packet.addData(Arrays.copyOfRange)
            packet.setCompleted(true);
        }
    }

    public static List<DataPacket> resumePacket(DataPacket packet)
    {
        List<DataPacket> out = new LinkedList<>();
        out.add(packet);
        //TENEMOS UN MENSAJE INCOMPLETO Y SEGUIMOS RELLENANDOLO

        return out;
    }

    public static byte[] intToByte(int num)
    {
        //ByteBuffer dbuf = ByteBuffer.allocate(2);
        //dbuf.putInt(num);
        //return dbuf.array();
        return ByteBuffer.allocate(2).putInt(num).array();
    }

    public static int byteToInt(byte[] num)
    {
        //ByteBuffer dbuf = ByteBuffer.wrap(num);
        //dbuf.putInt(num);
        //return dbuf.array();
        return ByteBuffer.wrap(num).getInt();
    }
}
