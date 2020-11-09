package Manager;

import com.fc.springcloud.service.Impl.ManagerServiceImpl;
import com.fc.springcloud.service.ManagerService;
import org.apache.catalina.Manager;

import java.io.IOException;

public class testManager {
    public static void main(String[] args) throws IOException {
        ManagerService manager = new ManagerServiceImpl();

//        String createResult = manager.CreateFunction("test7", "/Users/chenpeng/Desktop/jcc-jointFass/code", "python2.7", "counter.myhandler");
        String result = null;
        result = manager.DeleteFunction("test7");
        System.out.println(manager.ListFunction());
    }
}
