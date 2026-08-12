from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact replacement, found {count}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "kanger/src/org/kanger/Mind.java",
    "import org.kanger.factory.*;\n",
    "import org.kanger.factory.*;\nimport org.kanger.exception.TransactionSettlementException;\n")

replace_once(
    "kanger/src/org/kanger/Mind.java",
    '''                        finishTransactionLocked();
                        reservationFinished = true;
                        copyCommitResult(child);
                        return false;
''',
    '''                        boolean rootQuiescent = finishTransactionReservationLocked();
                        reservationFinished = true;
                        try {
                            finalizeTransactionRootLocked(rootQuiescent);
                            copyCommitResult(child);
                        } catch (Throwable finalizationFailure) {
                            throw new TransactionSettlementException(
                                    TransactionSettlementException.Outcome.REJECTED,
                                    finalizationFailure);
                        }
                        return false;
''')

replace_once(
    "kanger/src/org/kanger/Mind.java",
    '''                finishTransactionLocked();
                reservationFinished = true;
                copyCommitResult(child);
                return true;
''',
    '''                boolean rootQuiescent = finishTransactionReservationLocked();
                reservationFinished = true;
                try {
                    finalizeTransactionRootLocked(rootQuiescent);
                    copyCommitResult(child);
                } catch (Throwable finalizationFailure) {
                    throw new TransactionSettlementException(
                            TransactionSettlementException.Outcome.COMMITTED,
                            finalizationFailure);
                }
                return true;
''')

replace_once(
    "kanger/src/org/kanger/Mind.java",
    '''    private void finishTransactionLocked() throws Exception {
        if (transactionCounter <= 0) {
            throw new IllegalStateException("Transaction counter underflow for Mind " + id);
        }
        --transactionCounter;
        if (next == null && transactionCounter == 0) {
            pack();
            update();
        }
    }
''',
    '''    /**
     * Consumes exactly one child reservation and returns whether the current
     * Mind has reached root quiescence. This is the irreversible settlement
     * boundary: callers must mark the reservation finished immediately after
     * this method returns, before pack/update/flush is attempted.
     */
    private boolean finishTransactionReservationLocked() {
        if (transactionCounter <= 0) {
            throw new IllegalStateException("Transaction counter underflow for Mind " + id);
        }
        --transactionCounter;
        return next == null && transactionCounter == 0;
    }

    /**
     * Runs root-only post-settlement work. Failure here cannot reopen or retry
     * the child transaction because its reservation has already been consumed.
     */
    private void finalizeTransactionRootLocked(boolean rootQuiescent) throws Exception {
        if (rootQuiescent) {
            pack();
            update();
        }
    }

    private void finishTransactionLocked() throws Exception {
        boolean rootQuiescent = finishTransactionReservationLocked();
        finalizeTransactionRootLocked(rootQuiescent);
    }
''')

replace_once(
    "kanger/src/org/kanger/Mind.java",
    ''' * them exactly one of two paths — {@link #commit(IMind)} or
 * {@link #release(IMind)}. Класс одновременно координирует фабрики единиц,
''',
    ''' * them exactly one of two paths — {@link #commit(IMind)} or
 * {@link #release(IMind)}. Once a reservation is consumed, later root
 * pack/update/flush failure is post-settlement finalization and must never be
 * interpreted as a retryable child transaction. Класс одновременно координирует фабрики единиц,
''')

replace_once(
    "kanger-server/src/org/kanger/MindLifecycleReactor.java",
    "import org.kanger.exception.StorageLifecycleException;\n",
    "import org.kanger.exception.StorageLifecycleException;\nimport org.kanger.exception.TransactionSettlementException;\n")

replace_once(
    "kanger-server/src/org/kanger/MindLifecycleReactor.java",
    '''        } catch (StorageLifecycleException rejected) {
            JSONObject result = error(rejected.getCode(), rejected.toString());
            if (rejected.getRequiredAction() != null) {
                result.put("required_action", rejected.getRequiredAction());
            }
            if (user != null && user.getCurrentMind() != null) {
                return decorate(result, user);
            }
            return result;
        } catch (Exception failure) {
''',
    '''        } catch (StorageLifecycleException rejected) {
            JSONObject result = error(rejected.getCode(), rejected.toString());
            if (rejected.getRequiredAction() != null) {
                result.put("required_action", rejected.getRequiredAction());
            }
            if (user != null && user.getCurrentMind() != null) {
                return decorate(result, user);
            }
            return result;
        } catch (TransactionSettlementException settled) {
            /*
             * The user-visible child is already finished here. Report the
             * finalization failure, but also expose the irreversible semantic
             * outcome so clients do not retry an already-consumed transaction.
             */
            JSONObject result = error(
                    "transaction_settlement_finalization_failed",
                    settled.toString())
                    .put("settlement", settled.getOutcome().name())
                    .put("semantic_applied", settled.isSemanticApplied())
                    .put("reservation_consumed", settled.isReservationConsumed())
                    .put("required_action", "VERIFY_CURRENT_STATE");
            if (user != null && user.getCurrentMind() != null) {
                return decorate(result, user);
            }
            return result;
        } catch (Exception failure) {
''')

replace_once(
    "kanger-server/test/org/kanger/WorkspaceStateReactorTest.java",
    '''            // OPEN -> use is no longer a storage switch. The active workspace
            // must remain projected unchanged until an explicit close.
            JSONObject rejectedOther = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "other"));
            assertEquals("error", rejectedOther.getString("result"),
                    rejectedOther.toString());
            assertEquals("STORAGE_ALREADY_OPEN",
                    rejectedOther.getString("code"));
            assertEquals("EXPLICIT_CLOSE_REQUIRED",
                    rejectedOther.getString("required_action"));
            assertStorage(rejectedOther, "nested.one",
                    "nested" + Enums.FILE_SEPARATOR + "one");

            JSONObject closeNested = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("close", ""));
            assertEquals("OK", closeNested.getString("result"),
                    closeNested.toString());
            assertFalse(closeNested.getJSONObject("workspace")
                    .getJSONObject("storage").getBoolean("active"));

            JSONObject other = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "other"));
            assertEquals("OK", other.getString("result"), other.toString());
            assertStorage(other, "other", "other");
''',
    '''            // OPEN A -> use B is now an atomic Core semantic rebase. With
            // no explicit overlays in this fixture, workspace projection should
            // simply move from nested.one U0 to other U0 in one response.
            JSONObject other = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", "other"));
            assertEquals("OK", other.getString("result"), other.toString());
            assertStorage(other, "other", "other");
            assertEquals(0, other.getJSONObject("workspace")
                    .getJSONObject("transaction").getInt("level"));
''')
