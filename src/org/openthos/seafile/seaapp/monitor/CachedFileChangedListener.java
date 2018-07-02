package org.openthos.seafile.seaapp.monitor;


import java.io.File;

import org.openthos.seafile.seaapp.Account;
import org.openthos.seafile.seaapp.SeafCachedFile;

interface CachedFileChangedListener {
    void onCachedBlocksChanged(Account account, SeafCachedFile cf, File file);

    void onCachedFileChanged(Account account, SeafCachedFile cf, File file);
}

