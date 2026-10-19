package se.lite.play.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen device login: instructions and code on the left, QR card on the right.
 * Shown before any video plays, so it sits on the opaque {@link Theme#BG}.
 */
public class LoginView extends LinearLayout {
    /** QR card content size in dp; generate the bitmap at {@link #qrSizePx}. */
    public static final int QR_DP = 280;
    private static final int COL_DP = 400;

    private final ImageView qr;
    private final TextView url, code, status;

    public LoginView(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(Theme.BG);
        setPadding(Theme.dp(c, Theme.SAFE_X), Theme.dp(c, Theme.SAFE_Y),
                Theme.dp(c, Theme.SAFE_X), Theme.dp(c, Theme.SAFE_Y));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.addView(Ui.text(c, Theme.TEXT_CAPTION, Theme.BOLD, Theme.ACCENT_ON_DARK, "TELIA PLAY"));
        TextView h = Ui.text(c, Theme.TEXT_DISPLAY, Theme.MEDIUM, Theme.TEXT, "Logga in");
        h.setPadding(0, Theme.dp(c, Theme.SP_XS), 0, Theme.dp(c, Theme.SP_XL));
        col.addView(h);
        col.addView(Ui.text(c, Theme.TEXT_BODY, Theme.REGULAR, Theme.TEXT_2,
                "Skanna QR-koden med mobilen, eller gå till"));
        url = Ui.text(c, Theme.TEXT_BODY, Theme.MEDIUM, Theme.ACCENT_ON_DARK, "");
        url.setPadding(0, Theme.dp(c, Theme.SP_XS), 0, Theme.dp(c, Theme.SP_L));
        col.addView(url);
        col.addView(Ui.text(c, Theme.TEXT_BODY, Theme.REGULAR, Theme.TEXT_2, "och ange koden"));

        code = Ui.text(c, Theme.TEXT_CODE, Theme.MONO, Theme.TEXT, "");
        code.setLetterSpacing(0.12f);
        GradientDrawable box = Ui.rounded(Theme.SURFACE, Theme.dp(c, Theme.R_M));
        box.setStroke(Theme.dp(c, 2), Theme.HAIRLINE);
        code.setBackground(box);
        code.setPadding(Theme.dp(c, Theme.SP_XL), Theme.dp(c, Theme.SP_M),
                Theme.dp(c, Theme.SP_XL), Theme.dp(c, Theme.SP_M));
        LayoutParams cp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        cp.topMargin = Theme.dp(c, Theme.SP_M);
        col.addView(code, cp);

        status = Ui.text(c, Theme.TEXT_LABEL, Theme.REGULAR, Theme.TEXT_3, "Väntar på inloggning …");
        status.setPadding(0, Theme.dp(c, Theme.SP_XL), 0, 0);
        col.addView(status);
        // Fixed column width: with WRAP_CONTENT the instructions wrapped and their last line was cut.
        addView(col, new LayoutParams(Theme.dp(c, COL_DP), LayoutParams.WRAP_CONTENT));

        FrameLayout card = new FrameLayout(c);
        card.setBackground(Ui.rounded(0xFFFFFFFF, Theme.dp(c, Theme.R_XL)));
        int pad = Theme.dp(c, Theme.SP_L);
        card.setPadding(pad, pad, pad, pad);
        qr = new ImageView(c);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int q = Theme.dp(c, QR_DP);
        card.addView(qr, new FrameLayout.LayoutParams(q, q));
        LayoutParams kp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        kp.leftMargin = Theme.dp(c, 96);
        addView(card, kp);
        setVisibility(GONE);
    }

    /** Pixel size to generate the QR bitmap at, so it is drawn 1:1 without scaling. */
    public static int qrSizePx(Context c) {
        return Theme.dp(c, QR_DP);
    }

    /** Sets the QR bitmap, drawn without filtering so module edges stay sharp. */
    public void setQr(Bitmap b) {
        qr.setImageBitmap(b);
        Drawable d = qr.getDrawable();
        if (d instanceof BitmapDrawable) ((BitmapDrawable) d).setFilterBitmap(false);
    }

    /** Shows the address to type by hand: host only ("login.teliaplay.se"); the QR carries the full link. */
    public void setUrl(String verificationUri) {
        String u = verificationUri == null ? "" : verificationUri.replaceFirst("^https?://", "");
        int slash = u.indexOf('/');
        url.setText(slash > 0 ? u.substring(0, slash) : u);
    }

    /** The code, grouped in threes for reading aloud and typing ("766 162"). */
    public void setCode(String userCode) {
        String s = userCode == null ? "" : userCode;
        code.setText(s.length() == 6 && s.indexOf(' ') < 0 ? s.substring(0, 3) + " " + s.substring(3) : s);
    }

    /** Status line under the code, e.g. "Väntar på inloggning …", "Inloggad", an error. */
    public void setStatus(String text, boolean error) {
        status.setText(text);
        status.setTextColor(error ? Theme.ERROR : Theme.TEXT_3);
    }
}
