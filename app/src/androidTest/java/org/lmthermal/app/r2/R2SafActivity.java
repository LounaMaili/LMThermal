package org.lmthermal.app.r2;

import android.app.Activity;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.system.Os;
import android.system.OsConstants;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;

/** Standalone test APK storage probe. No camera owner, controls, product UI or format adoption. */
public final class R2SafActivity extends Activity {
    private TextView status;
    private File source;
    private static String hash(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data); StringBuilder text = new StringBuilder();
        for (byte value : digest) text.append(String.format("%02x", value & 255)); return text.toString();
    }
    private byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[65536]; int count;
        while ((count=input.read(buffer)) >= 0) { bytes.write(buffer,0,count); if (bytes.size()>16*1048576) throw new IOException("probe limit"); }
        return bytes.toByteArray();
    }
    private void report(JSONObject report) {
        try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(),"r2-saf-report.json"))) {
            output.write(report.toString(2).getBytes("UTF-8")); output.getFD().sync();
        } catch (Exception error) { report.optString("error", error.toString()); }
        runOnUiThread(() -> status.setText(report.toString()));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        source=new File(getFilesDir(),"r2-seed.r2proto");
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(this); status.setText("R2 diagnostic SAF probe — no camera use. Choose a LOCAL destination. Source is synthetic/noncanonical.");
        layout.addView(status); Button choose=new Button(this); choose.setText("Choose R2 test destination"); layout.addView(choose);
        choose.setOnClickListener(view -> {
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.setType("application/octet-stream");
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.putExtra(Intent.EXTRA_TITLE,"lmthermal-r2-saf-test.r2proto");
            startActivityForResult(intent,41);
        }); setContentView(layout);
        if (getIntent().hasExtra("probe_uri")) {
            new Thread(() -> {
                JSONObject result=new JSONObject();
                try { Uri uri=Uri.parse(getIntent().getStringExtra("probe_uri"));
                    try (ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r")) { result.put("permission_after_task_end","still_accessible"); }
                } catch (Exception error) { try { result.put("permission_after_task_end","denied"); result.put("error_class",error.getClass().getSimpleName()); } catch(Exception ignored){} }
                report(result);
            },"r2-permission-probe").start();
        }
    }
    private void write(Uri uri, String mode, byte[] bytes, JSONObject result, String label) throws Exception {
        try (ParcelFileDescriptor descriptor=getContentResolver().openFileDescriptor(uri,mode)) {
            result.put(label+"_descriptor_mode",mode);
            try { result.put(label+"_seek", Os.lseek(descriptor.getFileDescriptor(),0,OsConstants.SEEK_CUR)); }
            catch (Exception error) { result.put(label+"_seek","unsupported"); }
            FileOutputStream output=new FileOutputStream(descriptor.getFileDescriptor()); output.write(bytes); output.flush();
            try { descriptor.getFileDescriptor().sync(); result.put(label+"_sync",true); }
            catch (Exception error) { result.put(label+"_sync",false); throw error; }
            // Descriptor owns close; do not give two wrappers ownership of the same fd.
        }
        result.put(label+"_close",true);
    }
    @Override protected void onActivityResult(int request,int code,Intent intent) {
        super.onActivityResult(request,code,intent); if (request!=41) return;
        new Thread(() -> {
            JSONObject result=new JSONObject();
            try {
                byte[] original; try (InputStream in=new FileInputStream(source)) { original=read(in); }
                String before=hash(original); result.put("source_sha256",before);
                if (code!=RESULT_OK || intent==null || intent.getData()==null) { result.put("status","cancelled"); report(result); return; }
                Uri uri=intent.getData(); result.put("uri",uri.toString()); result.put("authority",uri.getAuthority());
                android.content.pm.ProviderInfo provider=getPackageManager().resolveContentProvider(uri.getAuthority(),0);
                result.put("provider_class",provider==null?"unknown":provider.name); result.put("capacity","unknown");
                result.put("capacity_warning_allows_start",true); result.put("grant_flags",intent.getFlags());
                write(uri,"rw",original,result,"write");
                byte[] copied; try (InputStream in=getContentResolver().openInputStream(uri)) { copied=read(in); }
                result.put("readback_exact",java.util.Arrays.equals(original,copied));
                write(uri,"wa",new byte[]{82,50},result,"append");
                try (InputStream in=getContentResolver().openInputStream(uri)) { result.put("append_length",read(in).length); }
                write(uri,"wt",original,result,"restore");
                try (InputStream in=getContentResolver().openInputStream(uri)) { copied=read(in); }
                result.put("final_readback_exact",java.util.Arrays.equals(original,copied)); result.put("bytes",original.length);
                byte[] after; try (InputStream in=new FileInputStream(source)) { after=read(in); }
                result.put("source_immutable",before.equals(hash(after)));
                if (!java.util.Arrays.equals(original,copied)) throw new IOException("readback mismatch");
                result.put("status","verified_export");
            } catch (Exception error) { try { result.put("status","storage_failure"); result.put("error_class",error.getClass().getSimpleName()); } catch(Exception ignored){} }
            report(result);
        },"r2-saf-io").start();
    }
}
