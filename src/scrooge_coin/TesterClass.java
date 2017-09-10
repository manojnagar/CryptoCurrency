package scrooge_coin;

import java.security.*;

public class TesterClass {

    public static void main(String[] args) {
        // write your code here
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            keyGen.initialize(1024, random);

            // Scrooge
            KeyPair pair = keyGen.generateKeyPair();
            PrivateKey sk_scrooge = pair.getPrivate();
            PublicKey pk_scrooge = pair.getPublic();

            // Bob

            pair = keyGen.generateKeyPair();
            PrivateKey sk_bob = pair.getPrivate();
            PublicKey pk_bob = pair.getPublic();

            // Maria

            pair = keyGen.generateKeyPair();
            PrivateKey sk_maria = pair.getPrivate();
            PublicKey pk_maria = pair.getPublic();

            // mayur

            pair = keyGen.generateKeyPair();
            PrivateKey sk_mayur = pair.getPrivate();
            PublicKey pk_mayur = pair.getPublic();

            // heidi

            pair = keyGen.generateKeyPair();
            PrivateKey sk_heidi = pair.getPrivate();
            PublicKey pk_heidi = pair.getPublic();

            Transaction tx1 = new Transaction();
            tx1.addOutput(10, pk_scrooge);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(sk_scrooge);
            sig.update(tx1.getRawTx());
            byte[] hash = sig.sign();
            tx1.setHash(hash);

            UTXOPool uPool = new UTXOPool();
            UTXO utxo = new UTXO(hash, 0);
            uPool.addUTXO(utxo, tx1.getOutput(0));

            Transaction tx2 = new Transaction();
            tx2.addInput(tx1.getHash(), 0);
            tx2.addOutput(4, pk_bob);
            tx2.addOutput(4, pk_heidi);
            tx2.addOutput(2, pk_scrooge);
            byte[] data = tx2.getRawDataToSign(0);

            Signature inputSig = Signature.getInstance("SHA256withRSA");
            inputSig.initSign(sk_scrooge);
            inputSig.update(data);
            byte[] inputHash = inputSig.sign();
            tx2.addSignature(inputHash, 0);

            Signature sig2 = Signature.getInstance("SHA256withRSA");
            sig2.initSign(sk_scrooge);
            sig2.update(tx2.getRawTx());
            byte[] hash2 = sig2.sign();
            tx2.setHash(hash2);

            Transaction tx3 = new Transaction();
            tx3.addInput(tx2.getHash(), 1);
            tx3.addInput(tx2.getHash(), 0);
            tx3.addOutput(1, pk_mayur);
            tx3.addOutput(1, pk_maria);
            tx3.addOutput(5, pk_scrooge);

            byte[] inputData1 = tx3.getRawDataToSign(0);
            Signature inputSig1 = Signature.getInstance("SHA256withRSA");
            inputSig1.initSign(sk_heidi);
            inputSig1.update(inputData1);
            byte[] inputHash1 = inputSig1.sign();
            tx3.addSignature(inputHash1, 0);

            byte[] inputData2 = tx3.getRawDataToSign(1);
            Signature inputSig2 = Signature.getInstance("SHA256withRSA");
            inputSig2.initSign(sk_bob);
            inputSig2.update(inputData2);
            byte[] inputHash2 = inputSig2.sign();
            tx3.addSignature(inputHash2, 1);

            Signature sig3 = Signature.getInstance("SHA256withRSA");
            sig3.initSign(sk_bob);
            sig3.update(tx2.getRawTx());
            byte[] hash3 = sig3.sign();
            tx3.setHash(hash3);

            Transaction[] tArray = new Transaction[2];
            tArray[0] = tx2;
            tArray[1] = tx3;
            //tArray[2] = tx3;

            TxHandler txHandler = new TxHandler(uPool);
            Transaction[] result = txHandler.handleTxs(tArray);
            System.out.println(result);

        } catch (Exception e) {

        }
    }

}
