-- Disposable runner only. This transaction leaves no source or user behind.
BEGIN;
DO $check$
BEGIN
 IF (SELECT used_bytes FROM market_storage_quota WHERE id=1) <> 0 THEN RAISE EXCEPTION 'Unexpected starting quota'; END IF;
 IF (SELECT data_type FROM information_schema.columns WHERE table_name='market_sources' AND column_name='original_bytes') <> 'bytea' THEN RAISE EXCEPTION 'Original is not bytea'; END IF;
 BEGIN
  UPDATE market_storage_quota SET used_bytes=104857601 WHERE id=1;
  RAISE EXCEPTION 'Quota constraint missing';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
  UPDATE market_storage_quota SET used_bytes=-1 WHERE id=1;
  RAISE EXCEPTION 'Negative quota accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 IF (SELECT numeric_precision FROM information_schema.columns WHERE table_name='market_reference_versions' AND column_name='area') <> 19 THEN RAISE EXCEPTION 'Source area precision changed'; END IF;
 IF (SELECT numeric_scale FROM information_schema.columns WHERE table_name='market_reference_versions' AND column_name='price') <> 2 THEN RAISE EXCEPTION 'Price scale changed'; END IF;
END;
$check$;
ROLLBACK;
