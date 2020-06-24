package com.alphawallet.app.ui.zxing;

import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.alphawallet.app.C;
import com.alphawallet.app.R;
import com.alphawallet.app.entity.CryptoFunctions;
import com.alphawallet.app.entity.QrUrlResult;
import com.alphawallet.app.repository.EthereumNetworkRepository;
import com.alphawallet.app.ui.BaseActivity;
import com.alphawallet.app.ui.widget.OnQRCodeScannedListener;
import com.alphawallet.app.util.QRURLParser;
import com.alphawallet.app.viewmodel.QRScanningViewModel;
import com.alphawallet.app.viewmodel.QRScanningViewModelFactory;
import com.alphawallet.token.entity.SalesOrderMalformed;
import com.alphawallet.token.tools.ParseMagicLink;

import java.util.Objects;

import javax.inject.Inject;

import dagger.android.AndroidInjection;

public class QRScanningActivity extends BaseActivity implements OnQRCodeScannedListener {

    @Inject
    QRScanningViewModelFactory qrScanningViewModelFactory;
    private QRScanningViewModel viewModel;

    private static final int RC_HANDLE_CAMERA_PERM = 2;

    public static final int DENY_PERMISSION = 1;

    private FullScannerFragment fullScannerFragment;

    @Override
    public void onCreate(Bundle state)
    {
        AndroidInjection.inject(this);

        super.onCreate(state);
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED)
        {
            setContentView(R.layout.activity_full_screen_scanner_fragment);
            initView();
        }
        else
        {
            requestCameraPermission();
        }
    }

    private void initView()
    {
        toolbar();
        enableDisplayHomeAsUp();
        setTitle(getString(R.string.action_scan_dapp));

        fullScannerFragment = (FullScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment);

        if(getIntent().getExtras() != null && getIntent().getExtras().containsKey(C.EXTRA_UNIVERSAL_SCAN))
        {
            Objects.requireNonNull(fullScannerFragment).registerListener(this);
        }

        viewModel = ViewModelProviders.of(this, qrScanningViewModelFactory)
                .get(QRScanningViewModel.class);
        viewModel.prepare(this);
    }

    // Handles the requesting of the camera permission.
    private void requestCameraPermission()
    {
        Log.w("QR SCanner", "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};
        ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM); //always ask for permission to scan
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean handled = false;

        if (requestCode == RC_HANDLE_CAMERA_PERM)
        {
            for (int i = 0; i < permissions.length; i++)
            {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.CAMERA))
                {
                    if (grantResult == PackageManager.PERMISSION_GRANTED)
                    {
                        setContentView(R.layout.activity_full_screen_scanner_fragment);
                        initView();
                        handled = true;
                    }
                }
            }
        }

        // Handle deny permission
        if (!handled)
        {
            Intent intent = new Intent();
            setResult(DENY_PERMISSION, intent);
            finish();
        }
    }

    @Override
    public void onReceive(String result)
    {
        handleQRCode(result);
    }

    public void handleQRCode(String qrCode)
    {
        try
        {
            if (qrCode == null || checkForMagicLink(qrCode)) return;

            QRURLParser parser = QRURLParser.getInstance();
            QrUrlResult qrUrlResult = parser.parse(qrCode);

            switch (qrUrlResult.type)
            {
                case ADDRESS:
                    //TODO
                    break;
                case PAYMENT:
                    viewModel.showSend(this, qrUrlResult);
                    finish();
                    break;
                case TRANSFER:
                    viewModel.showSend(this, qrUrlResult);
                    finish();
                    break;
                case FUNCTION_CALL:
                    //TODO
                    break;
                case URL:
                    String finalQrCode = qrCode;
                    viewModel.loadUrl(this, finalQrCode);
                    break;
                case OTHER:
                    qrCode = null;
                    break;
            }
        }
        catch (Exception e)
        {
            qrCode = null;
        }

        if(qrCode != null)
        {
            finish();
        }
        else
        {
            Toast.makeText(this, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show();
            fullScannerFragment.onResume();
        }
    }

    private boolean checkForMagicLink(String data)
    {
        try
        {
            ParseMagicLink parser = new ParseMagicLink(new CryptoFunctions(), EthereumNetworkRepository.extraChains());
            if (parser.parseUniversalLink(data).chainId > 0) //see if it's a valid link
            {
                //handle magic link import
                viewModel.showImportLink(this, data);
                return true;
            }
        }
        catch (SalesOrderMalformed e)
        {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public void onBackPressed()
    {
        viewModel.finishWithCancel(this);
    }
}
