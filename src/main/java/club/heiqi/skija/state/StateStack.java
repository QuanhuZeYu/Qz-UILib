package club.heiqi.skija.state;

import java.util.Stack;

public class StateStack {
    public final Stack<GLStateUtil> stateStack = new Stack<>();

    public void backup() {
        GLStateUtil curState = new GLStateUtil();
        curState.backupCurrentState();
        stateStack.push(curState);
    }

    public void restore() {
        if (stateStack.isEmpty()) return;
        GLStateUtil prevState = stateStack.pop();
        prevState.restorePreviousState();
    }
}
