package scrooge_coin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MaxFeeTxHandler {

	private UTXOPool utxoPool;
	
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
    	return isValidTx(utxoPool, tx);
    }
    
    /**
     * Validate a transaction in the given utxoPool
     * @param utxoPool
     * @param tx
     * @return true or false
     */
    private boolean isValidTx(UTXOPool utxoPool, Transaction tx) {
    	// Return false if transaction is null
    	if (tx == null ) {
    		return false;
    	}
    	
    	Set<Integer> checkUnique = new HashSet<Integer>(); 
    	double totalInValue = 0;
    	for(int index=0; index < tx.numInputs(); index++) {
    		Transaction.Input input = tx.getInput(index);
    		UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    		
    		//Check this utxo exist in pool : Case-1
    		if(!utxoPool.contains(utxo)) {
    			return false;
    		}
    		
    		//Duplicate utxo in input, double spend : Case-3
    		if(checkUnique.contains(utxo.hashCode())) {
    			return false;
    		}
    		checkUnique.add(utxo.hashCode());
    		
    		Transaction.Output userOutput = utxoPool.getTxOutput(utxo);
    		//Signature not match for a user : Case-2
    		if(!Crypto.verifySignature(userOutput.address, tx.getRawDataToSign(index), input.signature)) {
    			return false;
    		}
    		totalInValue += userOutput.value;
    	}
    	
    	double totalOutValue = 0;
    	for(int index = 0; index < tx.numOutputs(); index++) {
    		Transaction.Output output = tx.getOutput(index);
    		// Output value is negative : Case-4
    		if (output.value < 0) {
    			return false;
    		}
    		totalOutValue += output.value;
    	}
    	
    	//OutValue is greater than to InValue : Case-5
    	if(totalInValue < totalOutValue) {
    		return false;
    	}
    	return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> txs =  Arrays.asList(possibleTxs);
        Response response = getMaxProfitValidTxs(utxoPool, txs);
        
        List<Transaction> resultTxs = response.txs;
        return resultTxs.toArray(new Transaction[resultTxs.size()]);
    }
    
    private Response getMaxProfitValidTxs(UTXOPool utxoPool, List<Transaction> txs) {
   
    	//Check for empty or reach to the end
    	if(txs.isEmpty()) {
    		return new Response(0, new ArrayList<Transaction>());
    	}
    	
    	List<Transaction> nextValidTxs = new ArrayList<Transaction>();
    	List<Transaction> nextInValidTxs = new ArrayList<Transaction>();
    	
    	List<Transaction> result = new ArrayList<Transaction>();
    	double profit = 0;
    	
    	while(true) {
        	//Check for empty or reach to the end
        	if(txs.isEmpty()) {
        		break;
        	}
        	
        	List<Transaction> validTxs = new ArrayList<Transaction>();
        	List<Transaction> invalidTxs = new ArrayList<Transaction>();
        	for(Transaction tx : txs) {
        		if(isValidTx(utxoPool, tx)) {
        			validTxs.add(tx);
        		} else {
        			invalidTxs.add(tx);
        		}
        	}

        	//No valid transaction so : zero profit
        	if(validTxs.isEmpty()) {
        		nextInValidTxs = invalidTxs;
        		break;
        	}	
        
        	Set<Integer> tmpIndexs = new HashSet<>();
        	for(int index=0; index<validTxs.size(); index++) {
        		if(isNonConflict(index, validTxs)) {
        			tmpIndexs.add(index);
        		}
        	}
        	
        	List<Transaction> tmpValidTxs = new ArrayList<Transaction>();
        	for(int index=0; index< validTxs.size(); index++) {
        		Transaction tx = validTxs.get(index);
        		if(tmpIndexs.contains(index)) {
        				profit += getProfitForTransaction(utxoPool, tx);
            			updateTransactionIntoThePool(utxoPool, tx);
            			result.add(tx);		
        		} else {
        			tmpValidTxs.add(tx);
        		}
        	}
        	
        	nextValidTxs.addAll(tmpValidTxs);
            txs = invalidTxs;
    	}
    	
    	//No valid transaction
    	if(nextValidTxs.isEmpty()) {
    		return new Response(profit, result);
    	}    	
    	
    	int maxProfitIndex = 0;
    	double maxProfitValue = 0.0;
    	for(int index=0; index < nextValidTxs.size(); index++) {
    		UTXOPool localUtxoPool = new UTXOPool(utxoPool);
    		Transaction tx = new Transaction(nextValidTxs.get(index));
    		double localProfit = getProfitForTransaction(localUtxoPool, tx);
    		updateTransactionIntoThePool(localUtxoPool, tx);
    		
    		List<Transaction> localTxs = new ArrayList<Transaction>(nextValidTxs);
    		localTxs.remove(index);
    		localTxs.addAll(nextInValidTxs);
   
    		Response localResponse = getMaxProfitValidTxs(localUtxoPool, localTxs);
    		if((localResponse.profit + localProfit) > maxProfitValue) {
    			maxProfitIndex = index;
    			maxProfitValue = localResponse.profit;
    		}
    	}
    	
    	Transaction tx = nextValidTxs.get(maxProfitIndex);
    	profit += getProfitForTransaction(utxoPool, tx);
		updateTransactionIntoThePool(utxoPool, tx);
		result.add(tx);
 
	   	List<Transaction> nextTransaction = new ArrayList<Transaction>(nextValidTxs);
		nextTransaction.remove(maxProfitIndex);
    	nextTransaction.addAll(nextInValidTxs);
    	Response respone = getMaxProfitValidTxs(utxoPool, nextTransaction);
    
    	profit += respone.profit;
    	result.addAll(respone.txs);
    	return new Response(profit, result);
    }
    
    private boolean isNonConflict(int index, List<Transaction> txs) {
    	Transaction tx = txs.get(index);
    	for(int j=0; j < txs.size(); j++) {
    		if(index != j && isConflict(tx, txs.get(j))) {
    			return false;
    		}
    	}
    	return true;
    }
    
    private boolean isConflict(Transaction first, Transaction second) {
    	Set<Integer> checkUnique = new HashSet<>();
    	for(Transaction.Input input : first.getInputs()) {
    		UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    		checkUnique.add(utxo.hashCode());
    	}
    	
    	for(Transaction.Input input : second.getInputs()) {
    		UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    		if(checkUnique.contains(utxo.hashCode())) {
    			return true;
    		} else {
    			checkUnique.add(utxo.hashCode());
    		}
    	}
    	return false;
    }
    
    /**
     * Update the transaction in the given pool
     * @param utxoPool
     * @param tx
     */
    private void updateTransactionIntoThePool(UTXOPool utxoPool, Transaction tx) {
     	   tx.finalize();
     	   byte[] hash = tx.getHash();
     	   
     	   for(Transaction.Input input : tx.getInputs()) {
     		   UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
     		   utxoPool.removeUTXO(utxo);
     	   }
     	   
     	   for(int index = 0; index < tx.numOutputs(); index++) {
        			Transaction.Output output = tx.getOutput(index);
        			UTXO utxo = new UTXO(hash, index);
        			utxoPool.addUTXO(utxo, output);
     	   }
    }
       
    /**
     * Caller need to be ensure that transaction is valid for given pool
     * Get total profit from the transaction corresponding to given utxoPool
     * @param utxoPool
     * @param tx
     * @return total profit from the transaction
     */
    private double getProfitForTransaction(UTXOPool utxoPool, Transaction tx) {
    	double totalInValue = 0;
    	for(int index=0; index < tx.numInputs(); index++) {
    		Transaction.Input input = tx.getInput(index);
    		UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    		Transaction.Output userOutput = utxoPool.getTxOutput(utxo);
    		totalInValue += userOutput.value;
    	}
    	
    	double totalOutValue = 0;
    	for(int index = 0; index < tx.numOutputs(); index++) {
    		Transaction.Output output = tx.getOutput(index);
    		totalOutValue += output.value;
    	}
    	double totalProfit = totalInValue - totalOutValue;
    	
    	return totalProfit; 
    }
    
    private class Response {
    	double profit;
    	List<Transaction> txs;
    	
    	public Response(double profit, List<Transaction> txs) {
    		this.profit = profit;
    		this.txs = txs;
    	}
    }
}