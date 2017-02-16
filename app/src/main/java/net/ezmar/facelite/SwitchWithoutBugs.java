package net.ezmar.facelite;


import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

public class SwitchWithoutBugs extends SwitchPreference {
    public SwitchWithoutBugs(Context context) {
        super(context);
    }

    public SwitchWithoutBugs(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchWithoutBugs(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
}
