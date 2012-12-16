/*
 * Copyright (c) 2012. Krzysztof Otrebski
 * All right reserved
 */

package pl.otros.vfs.browser.auth;

import net.sf.vfsjfilechooser.utils.VFSURIParser;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.UserAuthenticationData;
import org.slf4j.Logger;

import javax.swing.*;
import java.util.Collection;


public class UseCentralsFromSessionUserAuthenticator extends  AbstractUiUserAuthenticator {

  private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(UseCentralsFromSessionUserAuthenticator.class);

  private final AuthStore sessionAuthStore;
  private final AbstractUiUserAuthenticator otrosUserAuthenticator;

  public UseCentralsFromSessionUserAuthenticator(AuthStore authStore,AuthStore sessionAuthStore, String url,
                                                 FileSystemOptions fileSystemOptions, AbstractUiUserAuthenticator otrosUserAuthenticator) {
    super(authStore, url, fileSystemOptions);
    this.sessionAuthStore = sessionAuthStore;
    this.otrosUserAuthenticator = otrosUserAuthenticator;
  }

  @Override
  public UserAuthenticationDataWrapper getLastUserAuthenticationData() {
    if (otrosUserAuthenticator!=null){
    return otrosUserAuthenticator.getLastUserAuthenticationData();
    }
      return null;

  }

  @Override
  public boolean isPasswordSave() {
    return otrosUserAuthenticator.isPasswordSave();
  }

  @Override
  protected void getAuthenticationData(UserAuthenticationData authenticationData) {
      otrosUserAuthenticator.getAuthenticationData(authenticationData);
  }

  @Override
  protected JPanel getOptionsPanel() {
    return otrosUserAuthenticator.getOptionsPanel();
  }

  @Override
  public UserAuthenticationData requestAuthentication(UserAuthenticationData.Type[] types) {
    UserAuthenticationData userAuthenticationData = getStaticWorkingUserAuthForSmb(sessionAuthStore, getUrl());
    if (userAuthenticationData ==null){
      userAuthenticationData = otrosUserAuthenticator.requestAuthentication(types);
    }
    return userAuthenticationData;
  }

  protected UserAuthenticationData getStaticWorkingUserAuthForSmb(AuthStore authStore, String url) {
    LOGGER.debug("Checking if have credentials for {}", url);
    VFSURIParser parser = new VFSURIParser(url);
    if (parser.getHostname() != null) {
      Collection<UserAuthenticationDataWrapper> userAuthenticationDatas = authStore.getUserAuthenticationDatas(parser.getProtocol().toString(),
          parser.getHostname());
      LOGGER.debug("Credentials count: {}", userAuthenticationDatas.size());
      if (userAuthenticationDatas.size() > 0) {
        UserAuthenticationData authenticationDataFromStore = userAuthenticationDatas.iterator().next();
        LOGGER.debug("Returning static authenticator for {}", url);
        return authenticationDataFromStore;
      }
    }
    LOGGER.debug("Do not have credentials for {}", url);
    return null;
  }
}
