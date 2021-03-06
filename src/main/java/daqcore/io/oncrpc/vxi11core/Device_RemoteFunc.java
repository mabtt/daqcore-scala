/*
 * Automatically generated by jrpcgen 1.0.7 on 19.05.11 01:50
 * jrpcgen is part of the "Remote Tea" ONC/RPC package for Java
 * See http://remotetea.sourceforge.net for details
 */
package daqcore.io.oncrpc.vxi11core;
import org.acplt.oncrpc.*;
import java.io.IOException;

public class Device_RemoteFunc implements XdrAble, java.io.Serializable {
    public int hostAddr;
    public int hostPort;
    public int progNum;
    public int progVers;
    public int progFamily;

    private static final long serialVersionUID = -5196012851908183122L;

    public Device_RemoteFunc() {
    }

    public Device_RemoteFunc(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        xdrDecode(xdr);
    }

    public void xdrEncode(XdrEncodingStream xdr)
           throws OncRpcException, IOException {
        xdr.xdrEncodeInt(hostAddr);
        xdr.xdrEncodeInt(hostPort);
        xdr.xdrEncodeInt(progNum);
        xdr.xdrEncodeInt(progVers);
        xdr.xdrEncodeInt(progFamily);
    }

    public void xdrDecode(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        hostAddr = xdr.xdrDecodeInt();
        hostPort = xdr.xdrDecodeInt();
        progNum = xdr.xdrDecodeInt();
        progVers = xdr.xdrDecodeInt();
        progFamily = xdr.xdrDecodeInt();
    }

}
// End of Device_RemoteFunc.java
