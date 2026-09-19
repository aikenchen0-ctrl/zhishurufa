package com.zhishurufa.sample.test;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;

/** 独立测试 APK 中的编辑器，不初始化或调用输入法 SDK。 */
public final class ExternalEditorActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        EditText editor = new EditText(this);
        editor.setContentDescription("external-editor");
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setGravity(android.view.Gravity.TOP);
        int padding = Math.round(32 * getResources().getDisplayMetrics().density);
        editor.setPadding(padding, padding, padding, padding);
        setContentView(editor, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        editor.requestFocus();
    }
}
