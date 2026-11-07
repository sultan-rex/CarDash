package com.sulthan.cardash;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Finds the parts of the screen that do not respond to touch. A grid covers the screen; every cell
 * a finger passes over turns teal. Rub a finger over the whole screen, then press back: the result
 * is saved to CarDash/touch-test.txt.
 *
 * Cells are kept in the glass's own position (as when the tablet is upright in its usual
 * landscape way round), so the result stays right when the screen flips 180 degrees.
 */
public class TouchTestActivity extends Activity {
    static final int COLS = 20, ROWS = 12;
    /** hits[row][col] in glass coordinates; the counts show how often each cell was touched. */
    private final int[][] hits = new int[ROWS][COLS];
    private Grid grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        grid = new Grid(this);
        setContentView(grid);
        Toast.makeText(this, "Rub a finger over the whole screen. Dark cells did not respond. Press back when done.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) MainActivity.hideSystemBars(this);
    }

    @Override
    public void onBackPressed() {
        save();
        super.onBackPressed();
    }

    /** True when the screen is flipped 180 degrees from the glass's usual landscape way round. */
    private boolean flipped() {
        return getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_270;
    }

    private void save() {
        int dead = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("CarDash touch test ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()))
                .append("\nGrid ").append(COLS).append(" x ").append(ROWS)
                .append(" in glass coordinates (row 0 = top, col 0 = left, as the screen normally faces).\n")
                .append("# = touched, . = never touched\n\n");
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                sb.append(hits[r][c] > 0 ? '#' : '.');
                if (hits[r][c] == 0) dead++;
            }
            sb.append('\n');
        }
        sb.append("\nnever touched: ").append(dead).append(" of ").append(ROWS * COLS).append('\n');
        try {
            File f = new File(Backup.folder(), "touch-test.txt");
            Backup.write(f, sb.toString());
            Toast.makeText(this, "Saved " + f.getPath() + " (" + dead + " dark cells)", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private final class Grid extends View {
        private final Paint on = new Paint(), off = new Paint(), line = new Paint();

        Grid(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.BLACK);
            on.setColor(0xFF2EC4B6);
            off.setColor(0xFF1A1A1A);
            line.setColor(Color.BLACK);
            line.setStrokeWidth(2);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            for (int p = 0; p < e.getPointerCount(); p++) {
                for (int h = 0; h < e.getHistorySize(); h++) mark(e.getHistoricalX(p, h), e.getHistoricalY(p, h));
                mark(e.getX(p), e.getY(p));
            }
            invalidate();
            return true;
        }

        private void mark(float x, float y) {
            int c = (int) (x * COLS / getWidth());
            int r = (int) (y * ROWS / getHeight());
            if (c < 0 || c >= COLS || r < 0 || r >= ROWS) return;
            if (flipped()) { c = COLS - 1 - c; r = ROWS - 1 - r; }
            hits[r][c]++;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cw = getWidth() / (float) COLS, rh = getHeight() / (float) ROWS;
            boolean flip = flipped();
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int gr = flip ? ROWS - 1 - r : r, gc = flip ? COLS - 1 - c : c;
                    canvas.drawRect(c * cw + 1, r * rh + 1, (c + 1) * cw - 1, (r + 1) * rh - 1,
                            hits[gr][gc] > 0 ? on : off);
                }
            }
        }
    }
}
