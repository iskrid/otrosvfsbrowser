/*
 * Copyright 2012 Krzysztof Otrebski (krzysztof.otrebski@gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package pl.otros.vfs.browser.auth;

public class StaticPasswordProvider implements PasswordProvider {

  private char[] password;

  public StaticPasswordProvider(char[] password) {
    this.password = password;
  }

  @Override
  public char[] getPassword(String message) {
    return password;
  }
}
