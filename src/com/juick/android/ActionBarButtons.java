package com.juick.android;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/3/13
 * Time: 9:00 AM
 * To change this template use File | Settings | File Templates.
 */
public class ActionBarButtons extends LinearLayout {

    public static class ActionButton {
        int resourceId;
        int id;

        public ActionButton(int resourceId, int id) {
            this.resourceId = resourceId;
            this.id = id;
        }

        ImageButton button;
    }

    ArrayList<ActionButton> currentActions = new ArrayList<ActionButton>();

    public ActionBarButtons(Context context) {
        super(context);
        init();
    }

    public ActionBarButtons(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ActionBarButtons(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setBackgroundColor(0xFF00FF00);
    }

    public ActionButton findAction(int id) {
        for (ActionButton currentAction : currentActions) {
            if (currentAction.id == id) return currentAction;
        }
        return null;
    }

    public void addActions(ActionButton[] actions) {
        for (ActionButton action : actions) {
            if (findAction(action.id) == null) {
                currentActions.add(action);
                action.button = new ImageButton(getContext());
                action.button.setBackgroundResource(action.resourceId);
                addView(action.button);
            }
        }
        recreate();
    }

    public void removeActions(ActionButton[] actions) {
        for (ActionButton action : actions) {
            ActionButton curr = findAction(action.id);
            if (curr != null) {
                currentActions.remove(curr);
                removeView(curr.button);
            }
        }
        recreate();
    }

    private void recreate() {
    }


}
