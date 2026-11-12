import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import java.util.Random;

public class CityCanvas extends GameCanvas implements Runnable {
    static final int T = 16, MW = 64, MH = 64, NC = 10, NP = 14, NCOP = 4;

    byte[] map = new byte[MW * MH]; // 0 road, 1 building, 2 grass
    Random rnd = new Random();

    int px = 24, py = 24, fx = 1, fy = 0;
    boolean inCar = false;
    int carIdx = -1;

    int[] cx = new int[NC], cy = new int[NC], cc = new int[NC];
    int[] ex = new int[NP], ey = new int[NP], evx = new int[NP], evy = new int[NP];
    int[] ecol = new int[NP], eres = new int[NP];
    int[] kx = new int[NCOP], ky = new int[NCOP];
    boolean[] kon = new boolean[NCOP];

    int wanted, health = 100, money, score, wtimer, cool, hurt, dead;
    int mx, my, rx, ry;

    volatile boolean run;
    Thread th;

    public CityCanvas() {
        super(true);
        setFullScreenMode(true);
        for (int y = 0; y < MH; y++) {
            for (int x = 0; x < MW; x++) {
                int t;
                if (x % 8 < 2 || y % 8 < 2) t = 0;
                else if (((x / 8) * 7 + (y / 8) * 13) % 5 == 0) t = 2;
                else t = 1;
                map[y * MW + x] = (byte) t;
            }
        }
        int[] cols = {0xcc2222, 0x2266cc, 0xdddd22, 0x22aa55, 0xeeeeee, 0xaa44aa};
        for (int i = 0; i < NC; i++) {
            randRoad();
            cx[i] = rx * T + 8;
            cy[i] = ry * T + 8;
            cc[i] = cols[i % cols.length];
        }
        cx[0] = 48;
        cy[0] = 24;
        for (int i = 0; i < NP; i++) spawnPed(i);
        newMarker();
    }

    public void start() {
        run = true;
        th = new Thread(this);
        th.start();
    }

    public void stop() {
        run = false;
    }

    int rand(int n) {
        return (rnd.nextInt() & 0x7fffffff) % n;
    }

    static int sg(int v) {
        return v > 0 ? 1 : (v < 0 ? -1 : 0);
    }

    void randRoad() {
        while (true) {
            int x = rand(MW), y = rand(MH);
            if (map[y * MW + x] == 0) {
                rx = x;
                ry = y;
                return;
            }
        }
    }

    void spawnPed(int i) {
        randRoad();
        ex[i] = rx * T + 8;
        ey[i] = ry * T + 8;
        evx[i] = rand(3) - 1;
        evy[i] = rand(3) - 1;
        ecol[i] = rand(0xffffff);
        eres[i] = 0;
    }

    void newMarker() {
        randRoad();
        mx = rx * T + 8;
        my = ry * T + 8;
    }

    boolean solid(int x, int y) {
        if (x < 0 || y < 0) return true;
        int tx = x / T, ty = y / T;
        if (tx >= MW || ty >= MH) return true;
        return map[ty * MW + tx] == 1;
    }

    boolean blocked(int x, int y, int h) {
        return solid(x - h, y - h) || solid(x + h, y - h)
            || solid(x - h, y + h) || solid(x + h, y + h);
    }

    public void run() {
        Graphics g = getGraphics();
        while (run) {
            long t = System.currentTimeMillis();
            update();
            draw(g);
            flushGraphics();
            long d = 40 - (System.currentTimeMillis() - t);
            try {
                Thread.sleep(d > 5 ? d : 5);
            } catch (Exception e) {
            }
        }
    }

    void useCar() {
        if (inCar) {
            inCar = false;
            cx[carIdx] = px;
            cy[carIdx] = py;
            carIdx = -1;
        } else {
            for (int i = 0; i < NC; i++) {
                if (Math.abs(cx[i] - px) < 14 && Math.abs(cy[i] - py) < 14) {
                    inCar = true;
                    carIdx = i;
                    cx[i] = -999;
                    cy[i] = -999;
                    break;
                }
            }
        }
    }

    void respawn() {
        px = 24;
        py = 24;
        health = 100;
        wanted = 0;
        money = money / 2;
        inCar = false;
        if (carIdx >= 0) {
            cx[carIdx] = 48;
            cy[carIdx] = 24;
            carIdx = -1;
        }
        for (int i = 0; i < NCOP; i++) kon[i] = false;
    }

    void update() {
        if (dead > 0) {
            dead--;
            if (dead == 0) respawn();
            return;
        }
        int k = getKeyStates();
        int dx = 0, dy = 0;
        if ((k & LEFT_PRESSED) != 0) dx = -1;
        else if ((k & RIGHT_PRESSED) != 0) dx = 1;
        if ((k & UP_PRESSED) != 0) dy = -1;
        else if ((k & DOWN_PRESSED) != 0) dy = 1;
        if (cool > 0) cool--;
        if ((k & FIRE_PRESSED) != 0 && cool == 0) {
            cool = 12;
            useCar();
        }
        int sp = inCar ? 4 : 2;
        if (dx != 0 || dy != 0) {
            fx = dx;
            fy = dy;
        }
        if (!blocked(px + dx * sp, py, 5)) px += dx * sp;
        if (!blocked(px, py + dy * sp, 5)) py += dy * sp;

        // pedestrians
        for (int i = 0; i < NP; i++) {
            if (eres[i] > 0) {
                eres[i]--;
                if (eres[i] == 0) spawnPed(i);
                continue;
            }
            if (rand(30) == 0) {
                evx[i] = rand(3) - 1;
                evy[i] = rand(3) - 1;
            }
            if (blocked(ex[i] + evx[i], ey[i], 3)) evx[i] = -evx[i];
            else ex[i] += evx[i];
            if (blocked(ex[i], ey[i] + evy[i], 3)) evy[i] = -evy[i];
            else ey[i] += evy[i];
            if (inCar && (dx != 0 || dy != 0)
                && Math.abs(ex[i] - px) < 9 && Math.abs(ey[i] - py) < 9) {
                eres[i] = 100;
                score += 10;
                if (wanted < 4) wanted++;
                wtimer = 400;
            }
        }

        // police
        for (int i = 0; i < NCOP; i++) {
            if (i >= wanted) {
                kon[i] = false;
                continue;
            }
            if (!kon[i]) {
                randRoad();
                if (Math.abs(rx * T - px) > 120 || Math.abs(ry * T - py) > 120) {
                    kx[i] = rx * T + 8;
                    ky[i] = ry * T + 8;
                    kon[i] = true;
                }
                continue;
            }
            int sx = sg(px - kx[i]), sy = sg(py - ky[i]);
            if (!blocked(kx[i] + sx * 2, ky[i], 5)) kx[i] += sx * 2;
            if (!blocked(kx[i], ky[i] + sy * 2, 5)) ky[i] += sy * 2;
            if (Math.abs(kx[i] - px) < 10 && Math.abs(ky[i] - py) < 10) {
                if (inCar) {
                    kon[i] = false;
                    score += 20;
                } else {
                    hurt++;
                    if (hurt % 4 == 0) health--;
                }
            }
        }

        if (wanted > 0) {
            wtimer--;
            if (wtimer <= 0) {
                wanted--;
                wtimer = 400;
            }
        }
        if (health <= 0) dead = 40;

        // mission marker
        if (Math.abs(mx - px) < 14 && Math.abs(my - py) < 14) {
            money += 100;
            newMarker();
        }
    }

    void draw(Graphics g) {
        int w = getWidth(), h = getHeight();
        int ox = px - w / 2, oy = py - h / 2;
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);
        int tx0 = ox / T - 1, ty0 = oy / T - 1;
        for (int ty = ty0; ty <= ty0 + h / T + 3; ty++) {
            for (int tx = tx0; tx <= tx0 + w / T + 3; tx++) {
                if (tx < 0 || ty < 0 || tx >= MW || ty >= MH) continue;
                int t = map[ty * MW + tx];
                int sx = tx * T - ox, sy = ty * T - oy;
                if (t == 0) g.setColor(0x505050);
                else if (t == 2) g.setColor(0x3c8c3c);
                else g.setColor(0x6a4a3a);
                g.fillRect(sx, sy, T, T);
                if (t == 1) {
                    int s = ((tx * 7 + ty * 3) % 3) * 0x151515;
                    g.setColor(0x9a9a9a + s);
                    g.fillRect(sx + 2, sy + 2, T - 4, T - 4);
                } else if (t == 0) {
                    g.setColor(0xdddd55);
                    if (tx % 8 == 0 && ty % 8 >= 2) g.fillRect(sx + T - 1, sy + 4, 1, 8);
                    if (ty % 8 == 0 && tx % 8 >= 2) g.fillRect(sx + 4, sy + T - 1, 8, 1);
                }
            }
        }

        // mission marker + arrow
        g.setColor(0xffff00);
        g.drawRect(mx - ox - 7, my - oy - 7, 14, 14);
        int ddx = mx - px, ddy = my - py;
        int m = Math.max(Math.abs(ddx), Math.abs(ddy)) + 1;
        g.drawLine(w / 2, h / 2, w / 2 + ddx * 20 / m, h / 2 + ddy * 20 / m);

        // parked cars
        for (int i = 0; i < NC; i++) {
            if (cx[i] < -900) continue;
            g.setColor(cc[i]);
            g.fillRect(cx[i] - ox - 6, cy[i] - oy - 4, 12, 8);
            g.setColor(0x222222);
            g.fillRect(cx[i] - ox + 1, cy[i] - oy - 3, 3, 6);
        }
        // pedestrians
        for (int i = 0; i < NP; i++) {
            if (eres[i] > 0) continue;
            g.setColor(ecol[i]);
            g.fillRect(ex[i] - ox - 2, ey[i] - oy - 2, 5, 5);
        }
        // police
        for (int i = 0; i < NCOP; i++) {
            if (!kon[i]) continue;
            g.setColor(0x2244ff);
            g.fillRect(kx[i] - ox - 3, ky[i] - oy - 3, 7, 7);
            g.setColor(0xff2222);
            g.fillRect(kx[i] - ox - 3, ky[i] - oy - 3, 7, 2);
        }
        // player
        if (inCar) {
            g.setColor(0xff3333);
            if (fy == 0) g.fillRect(w / 2 - 6, h / 2 - 4, 12, 8);
            else g.fillRect(w / 2 - 4, h / 2 - 6, 8, 12);
        } else {
            g.setColor(0x000000);
            g.fillRect(w / 2 - 4, h / 2 - 4, 8, 8);
            g.setColor(0xffdd00);
            g.fillRect(w / 2 - 3, h / 2 - 3, 6, 6);
        }

        // HUD
        g.setColor(0xffffff);
        g.drawString("$" + money + "  Score " + score, 2, 2, Graphics.TOP | Graphics.LEFT);
        g.setColor(0x333333);
        g.fillRect(2, 16, 42, 5);
        g.setColor(0x22cc22);
        g.fillRect(3, 17, health * 40 / 100, 3);
        String st = "";
        for (int i = 0; i < wanted; i++) st += "*";
        g.setColor(0xff5555);
        g.drawString(st, w - 2, 2, Graphics.TOP | Graphics.RIGHT);
        g.setColor(0xffffff);
        g.drawString(inCar ? "FIRE: exit car" : "FIRE: enter car", w / 2, h - 2,
            Graphics.BOTTOM | Graphics.HCENTER);
        if (dead > 0) {
            g.setColor(0xff0000);
            g.drawString("WASTED", w / 2, h / 2 - 20, Graphics.TOP | Graphics.HCENTER);
        }
    }
}
