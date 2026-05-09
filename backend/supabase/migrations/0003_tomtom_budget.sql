-- Migration: 0003_tomtom_budget
-- Daily TomTom API call counter shared between mobile app and backend.

CREATE TABLE IF NOT EXISTS tomtom_budget (
  date          date PRIMARY KEY,
  mobile_calls  int NOT NULL DEFAULT 0,
  backend_calls int NOT NULL DEFAULT 0
);

-- RPC: increment_tomtom_call – atomic counter increment.
-- scope: 'mobile' | 'backend'
CREATE OR REPLACE FUNCTION increment_tomtom_call(scope text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  today date := current_date;
BEGIN
  INSERT INTO tomtom_budget (date, mobile_calls, backend_calls)
  VALUES (today, 0, 0)
  ON CONFLICT (date) DO NOTHING;

  IF scope = 'mobile' THEN
    UPDATE tomtom_budget
    SET mobile_calls = mobile_calls + 1
    WHERE date = today;
  ELSIF scope = 'backend' THEN
    UPDATE tomtom_budget
    SET backend_calls = backend_calls + 1
    WHERE date = today;
  ELSE
    RAISE EXCEPTION 'Invalid scope: %. Must be ''mobile'' or ''backend''.', scope;
  END IF;
END;
$$;

-- RPC: get_tomtom_budget_today
CREATE OR REPLACE FUNCTION get_tomtom_budget_today()
RETURNS tomtom_budget
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(
    (SELECT * FROM tomtom_budget WHERE date = current_date),
    ROW(current_date, 0, 0)::tomtom_budget
  );
$$;
