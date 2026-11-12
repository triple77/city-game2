import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class CityMidlet extends MIDlet {
    CityCanvas canvas;

    protected void startApp() {
        if (canvas == null) {
            canvas = new CityCanvas();
            canvas.start();
        }
        Display.getDisplay(this).setCurrent(canvas);
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {
        if (canvas != null) canvas.stop();
    }
}
