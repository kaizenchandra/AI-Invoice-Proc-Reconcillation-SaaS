CREATE TABLE external_identities (
 issuer text NOT NULL,
 subject text NOT NULL,
 user_id uuid NOT NULL REFERENCES app_users(id),
 PRIMARY KEY(issuer,subject)
);
