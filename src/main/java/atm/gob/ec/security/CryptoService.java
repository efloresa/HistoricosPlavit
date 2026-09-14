/**
 *
 * @author erik.flores
 */

package atm.gob.ec.security;

public interface CryptoService {

    String encrypt(String value);

    String decrypt(String value);
}

