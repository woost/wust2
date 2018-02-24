--
-- PostgreSQL database dump
--

-- Dumped from database version 10.1
-- Dumped by pg_dump version 10.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

SET search_path = public, pg_catalog;

ALTER TABLE ONLY public.membership DROP CONSTRAINT usergroupmember_userid_fkey;
ALTER TABLE ONLY public.membership DROP CONSTRAINT usergroupmember_groupid_fkey;
ALTER TABLE ONLY public.groupinvite DROP CONSTRAINT usergroupinvite_groupid_fkey;
ALTER TABLE ONLY public.rawpost DROP CONSTRAINT rawpost_userid_fkey;
ALTER TABLE ONLY public.rawconnection DROP CONSTRAINT rawconnection_label_fkey;
ALTER TABLE ONLY public.password DROP CONSTRAINT password_id_fkey;
ALTER TABLE ONLY public.ownership DROP CONSTRAINT ownership_postid_fkey;
ALTER TABLE ONLY public.ownership DROP CONSTRAINT ownership_groupid_fkey;
ALTER TABLE ONLY public.rawconnection DROP CONSTRAINT connection_targetid_fkey;
ALTER TABLE ONLY public.rawconnection DROP CONSTRAINT connection_sourceid_fkey;
DROP TRIGGER vct_update ON public.connection;
DROP TRIGGER vct_insert ON public.connection;
DROP TRIGGER vct_delete ON public.connection;
DROP INDEX public.usergroupmember_userid_idx;
DROP INDEX public.usergroupmember_groupid_idx;
DROP INDEX public.usergroupinvite_token_idx;
DROP INDEX public.schema_version_s_idx;
DROP INDEX public.rawpost_isdeleted_idx;
DROP INDEX public.ownership_postid_idx;
DROP INDEX public.ownership_groupid_idx;
DROP INDEX public.lname;
ALTER TABLE ONLY public.membership DROP CONSTRAINT usergroupmember_groupid_userid_key;
ALTER TABLE ONLY public.groupinvite DROP CONSTRAINT usergroupinvite_token_key;
ALTER TABLE ONLY public.groupinvite DROP CONSTRAINT usergroupinvite_pkey;
ALTER TABLE ONLY public.usergroup DROP CONSTRAINT usergroup_pkey;
ALTER TABLE ONLY public."user" DROP CONSTRAINT user_pkey;
ALTER TABLE ONLY public."user" DROP CONSTRAINT user_name_key;
ALTER TABLE ONLY public.label DROP CONSTRAINT unique_label_name;
ALTER TABLE ONLY public.schema_version DROP CONSTRAINT schema_version_pk;
ALTER TABLE ONLY public.rawpost DROP CONSTRAINT post_pkey;
ALTER TABLE ONLY public.password DROP CONSTRAINT password_pkey;
ALTER TABLE ONLY public.ownership DROP CONSTRAINT ownership_postid_groupid_key;
ALTER TABLE ONLY public.label DROP CONSTRAINT label_pkey;
ALTER TABLE ONLY public.rawconnection DROP CONSTRAINT connection_pkey;
ALTER TABLE public.usergroup ALTER COLUMN id DROP DEFAULT;
ALTER TABLE public.label ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE public.usergroup_id_seq;
DROP TABLE public.usergroup;
DROP TABLE public."user";
DROP TABLE public.schema_version;
DROP VIEW public.post;
DROP TABLE public.rawpost;
DROP TABLE public.password;
DROP TABLE public.ownership;
DROP TABLE public.membership;
DROP SEQUENCE public.label_id_seq;
DROP TABLE public.groupinvite;
DROP VIEW public.connection;
DROP TABLE public.rawconnection;
DROP TABLE public.label;
DROP FUNCTION public.vc_update();
DROP FUNCTION public.vc_insert();
DROP FUNCTION public.vc_delete();
DROP FUNCTION public.now_utc();
DROP FUNCTION public.insert_label(_name text, OUT _lid integer);
DROP FUNCTION public.graph_component(start integer);
DROP FUNCTION public.get_label_id(_name text);
DROP EXTENSION plpgsql;
DROP SCHEMA public;
--
-- Name: public; Type: SCHEMA; Schema: -; Owner: wust
--

CREATE SCHEMA public;


ALTER SCHEMA public OWNER TO wust;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

--
-- Name: get_label_id(text); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION get_label_id(_name text) RETURNS integer
    LANGUAGE plpgsql
    AS $$
    BEGIN
        RETURN (SELECT id FROM label WHERE name = _name);
    END;
$$;


ALTER FUNCTION public.get_label_id(_name text) OWNER TO wust;

--
-- Name: graph_component(integer); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION graph_component(start integer) RETURNS SETOF integer
    LANGUAGE plpgsql
    AS $$
declare
    queue Integer[] := array[start];
begin
    create temporary table visited (id integer NOT NULL) on commit drop;
    create unique index on visited (id);

    WHILE array_length(queue,1) > 0 LOOP
        insert into visited (select unnest(queue)) on conflict do nothing;
        queue := array(
            select targetId
            from (select unnest(queue) as id) as q
            join _incidence on sourceId = q.id
            left outer join visited on targetId = visited.id
            where visited.id is NULL
        );
    END LOOP;
    return query (select id from visited);
end;
$$;


ALTER FUNCTION public.graph_component(start integer) OWNER TO wust;

--
-- Name: insert_label(text); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION insert_label(_name text, OUT _lid integer) RETURNS integer
    LANGUAGE plpgsql
    AS $$
    BEGIN
        SELECT id FROM label WHERE name = _name INTO _lid;
        IF NOT FOUND THEN
            INSERT INTO label (name) VALUES (_name) RETURNING id INTO _lid;
        END IF;
    END;
$$;


ALTER FUNCTION public.insert_label(_name text, OUT _lid integer) OWNER TO wust;

--
-- Name: now_utc(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION now_utc() RETURNS timestamp without time zone
    LANGUAGE sql
    AS $$
    SELECT NOW() AT TIME ZONE 'utc';
$$;


ALTER FUNCTION public.now_utc() OWNER TO wust;

--
-- Name: vc_delete(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION vc_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
        DECLARE
            _lid INTEGER;
            row_count INTEGER;
        BEGIN
            -- label
            _lid = get_label_id(OLD.label);

            -- delete
            DELETE FROM rawconnection
                WHERE sourceid = OLD.sourceid AND label = _lid AND targetid = OLD.targetid;

            GET DIAGNOSTICS row_count = ROW_COUNT;
            RAISE NOTICE 'Deleted % row(s) FROM rawconnection', row_count;

            RETURN OLD;
        END;
    $$;


ALTER FUNCTION public.vc_delete() OWNER TO wust;

--
-- Name: vc_insert(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION vc_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
        DECLARE
            _lid INTEGER;
            row_count INTEGER;
        BEGIN
            _lid = insert_label(NEW.label);

        INSERT INTO rawconnection (sourceid, targetid, label)
            SELECT NEW.sourceid, NEW.targetid, _lid
                WHERE NOT EXISTS (
                    SELECT TRUE FROM rawconnection
                        WHERE sourceid = NEW.sourceid
                        AND targetid = NEW.targetid
                        AND label = _lid
                );

            GET DIAGNOSTICS row_count = ROW_COUNT;
            RAISE NOTICE 'Inserted % row(s) FROM rawconnection', row_count;

            RETURN NEW;
        END;
    $$;


ALTER FUNCTION public.vc_insert() OWNER TO wust;

--
-- Name: vc_update(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION vc_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
        DECLARE
            _lid INTEGER;
            row_count INTEGER;
        BEGIN
            IF NEW.label <> OLD.label THEN
                _lid = insert_label(NEW.label);
            END IF;

            UPDATE rawconnection SET (sourceid, targetid, label) = (NEW.sourceid, NEW.targetid, _lid)
                WHERE sourceid = OLD.sourceid
                AND targetid = OLD.targetid
                AND label = OLD.label;

            GET DIAGNOSTICS row_count = ROW_COUNT;
            RAISE NOTICE 'Updated % row(s) FROM rawconnection', row_count;

            RETURN NEW.sourceid, _lid, NEW.targetid;
        END;
    $$;


ALTER FUNCTION public.vc_update() OWNER TO wust;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: label; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE label (
    id integer NOT NULL,
    name text NOT NULL
);


ALTER TABLE label OWNER TO wust;

--
-- Name: rawconnection; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE rawconnection (
    sourceid character varying(36) NOT NULL,
    targetid character varying(36) NOT NULL,
    label integer NOT NULL,
    CONSTRAINT selfloop CHECK (((sourceid)::text <> (targetid)::text))
);


ALTER TABLE rawconnection OWNER TO wust;

--
-- Name: connection; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW connection AS
 SELECT c.sourceid,
    l.name AS label,
    c.targetid
   FROM (rawconnection c
     JOIN label l ON ((c.label = l.id)));


ALTER TABLE connection OWNER TO wust;

--
-- Name: groupinvite; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE groupinvite (
    groupid integer NOT NULL,
    token text NOT NULL
);


ALTER TABLE groupinvite OWNER TO wust;

--
-- Name: label_id_seq; Type: SEQUENCE; Schema: public; Owner: wust
--

CREATE SEQUENCE label_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE label_id_seq OWNER TO wust;

--
-- Name: label_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: wust
--

ALTER SEQUENCE label_id_seq OWNED BY label.id;


--
-- Name: membership; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE membership (
    groupid integer NOT NULL,
    userid character varying(36) NOT NULL
);


ALTER TABLE membership OWNER TO wust;

--
-- Name: ownership; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE ownership (
    postid character varying(36) NOT NULL,
    groupid integer NOT NULL
);


ALTER TABLE ownership OWNER TO wust;

--
-- Name: password; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE password (
    id character varying(36) NOT NULL,
    digest bytea NOT NULL
);


ALTER TABLE password OWNER TO wust;

--
-- Name: rawpost; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE rawpost (
    id character varying(36) NOT NULL,
    content text NOT NULL,
    isdeleted boolean DEFAULT false NOT NULL,
    author character varying(36) NOT NULL,
    created timestamp without time zone DEFAULT now_utc() NOT NULL,
    modified timestamp without time zone DEFAULT now_utc() NOT NULL
);


ALTER TABLE rawpost OWNER TO wust;

--
-- Name: post; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW post AS
 SELECT rawpost.id,
    rawpost.content,
    rawpost.author,
    rawpost.created,
    rawpost.modified
   FROM rawpost
  WHERE (rawpost.isdeleted = false);


ALTER TABLE post OWNER TO wust;

--
-- Name: schema_version; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE schema_version (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE schema_version OWNER TO wust;

--
-- Name: user; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE "user" (
    id character varying(36) NOT NULL,
    name text NOT NULL,
    revision integer DEFAULT 0 NOT NULL,
    isimplicit boolean DEFAULT false NOT NULL
);


ALTER TABLE "user" OWNER TO wust;

--
-- Name: usergroup; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE usergroup (
    id integer NOT NULL
);


ALTER TABLE usergroup OWNER TO wust;

--
-- Name: usergroup_id_seq; Type: SEQUENCE; Schema: public; Owner: wust
--

CREATE SEQUENCE usergroup_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE usergroup_id_seq OWNER TO wust;

--
-- Name: usergroup_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: wust
--

ALTER SEQUENCE usergroup_id_seq OWNED BY usergroup.id;


--
-- Name: label id; Type: DEFAULT; Schema: public; Owner: wust
--

ALTER TABLE ONLY label ALTER COLUMN id SET DEFAULT nextval('label_id_seq'::regclass);


--
-- Name: usergroup id; Type: DEFAULT; Schema: public; Owner: wust
--

ALTER TABLE ONLY usergroup ALTER COLUMN id SET DEFAULT nextval('usergroup_id_seq'::regclass);


--
-- Data for Name: groupinvite; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY groupinvite (groupid, token) FROM stdin;
\.


--
-- Data for Name: label; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY label (id, name) FROM stdin;
1	related
2	parent
3	describes
\.


--
-- Data for Name: membership; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY membership (groupid, userid) FROM stdin;
\.


--
-- Data for Name: ownership; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY ownership (postid, groupid) FROM stdin;
\.


--
-- Data for Name: password; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY password (id, digest) FROM stdin;
\.


--
-- Data for Name: rawconnection; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY rawconnection (sourceid, targetid, label) FROM stdin;
239213727	100032650	2
239028450	100032650	2
144926171	100032650	2
239028022	100032650	2
238991027	100032650	2
239419668	100032650	2
239700913	100032650	2
239276831	100032650	2
204150219	100032650	2
239026911	100032650	2
239005073	100032650	2
239026417	100032650	2
239249046	100032650	2
239334399	100032650	2
239231525	100032650	2
\.


--
-- Data for Name: rawpost; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY rawpost (id, content, isdeleted, author, created, modified) FROM stdin;
204150219	That could be a nice simple solution to this problem. We've also discussed outputing to a pager of some kind to help you scroll through the errors.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-04-01 00:04:20	2016-04-01 00:04:20
239276831	We discussed this in the @rust-lang/compiler meeting, so I'm removing the nominated tag -- there was not a lot of enthusiasm, for the reasons that have been presented on the thread already (e.g. by @nrc).\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 22:10:20	2016-08-11 22:10:20
239028450	Worth mentioning, too, that the errors are streamed rather than buffered.  That's to give feedback sooner rather than forcing the programmer to wait.\n\nAs we add more error recovery to the compiler, it will likely mean longer wait times before you see an error if you buffer until the compile finishes because the compiler will be able to recover and go for longer in the presence of errors.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 00:48:54	2016-08-11 00:48:54
239026417	My feelings on this are close to @nrc's.  \nHowever, as a practical matter:\n- Does Linux have any standard tools that can parse and page JSON record-by-record? \n- Even the above exists, you still need a tool that will take JSON output and convert it into human-readable format.\n\nPerhaps we could settle on adding rustc parameter to suppress errors beyond the first N?\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 00:38:46	2016-08-11 01:30:13
239231525	I’m very against using any sort of pager. Any proper terminal emulator is already as functional or more functional than your average pager, why cripple that? Perhaps a simpler solution is to not use rustc in getty?\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 19:29:09	2016-08-11 19:29:56
239249046	@nagisa presumably you could control it in whatever way e.g. git lets you control it. But a pager has a lot of advantages. For example, you see the first error... first. If you have to page-up as in a "proper" terminal emulator, that's annoying, because you can easily page up into older errors. If your terminal emulator is so fancy as to somehow jump to the top of the current rustc run, that's great, but many people are just using Terminal.app or whatever.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 20:30:00	2016-08-11 20:30:00
239028022	To pile on a "me too" to @nrc and @vadimcn - it'd be much better to use a second tool to turn the output from the compiler into what you want.  There some precedence, like tools like [dybuk](https://github.com/ticki/dybuk) do their own parsing and recreating the errors.  I can imagine a tool that reads in the errors and then replays them backward as another one.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 00:46:42	2016-08-11 00:46:42
239213727	I'm much more in favor of using a pager; but I guess that doesn't help with `cargo watch`. Still, `cargo watch` could capture the output and reverse the order itself, which seems better. (I'll note that we added the "easy presentation" info to JSON that I've been talking about, this would be a great use-case for it. =)\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 18:25:29	2016-08-11 18:25:29
144926171	I just had this idea while scrolling upwards in my terminal to find the first compile error:\nIt would be nice to have the option to print the compile errors in reverse order, then one sees the most relevant error at a first glance. This would be especially helpful when using cargo-watch.\n\n(I'm posting this here, because this is not a language RFC, was that correct?)\n	f	cjdvvfng90004vk44cnu0ml2g	2016-03-31 16:58:40	2016-08-15 00:10:46
100032650	#32650 option to reverse the order of compile error messages 	f	cjdvvfng90004vk44cnu0ml2g	2016-03-31 16:58:40	2016-08-15 00:10:46
239334399	Since there's little change of this enhancement happening I'm just closing.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-12 02:41:38	2016-08-12 02:41:38
239700913	@fdietze I recommend running the output of the compiler through a tool to get the output you want. Exactly which tool depends on what you want - but something as simple as `less` might be enough. Or maybe a more sophisticated pager?\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-15 00:10:46	2016-08-15 00:10:46
239005073	cc @rust-lang/tools too\n\nI'm a little wary about this, although it is small in itself, I can imagine we then end up adding paging options or other error display stuff to the compiler and end up with a pile of command line options, etc. \n\nI'd prefer that the compiler just offers a single best-effort error message presentation style, and if you want something else, you run the compiler through an external tool (command-line or otherwise) that takes JSON errors as input and outputs errors in whatever format you like.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-10 23:08:38	2016-08-10 23:08:38
239026911	You could do what Git does and automatically send output longer than the number of rows on the terminal to `$PAGER`.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-11 00:41:16	2016-08-11 00:41:16
238991027	Nominating for discussion. @rust-lang/compiler @jonathandturner @GuillaumeGomez this is conceptually quite simple to do. Do we want to write up the tasks and ask for contributions?\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-10 22:19:45	2016-08-10 22:19:45
239419668	So what is the recommended way of dealing with this problem now?\n\nOff Topic:\nI tried to model this discussion as a graph.\nhttp://lanzarote.informatik.rwth-aachen.de:9001/focus/HJ_DHGmLQeaSciC0j_haSQ/graph\nAlthough there were some things that I couldn't express in the graph, I think It's easier to grasp than reading this thread.\n	f	cjdvvfng90004vk44cnu0ml2g	2016-08-12 13:05:05	2016-08-12 13:05:05
\.


--
-- Data for Name: schema_version; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY schema_version (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	graph	SQL	V1__graph.sql	422010744	wust	2018-02-20 16:30:20.300589	27	t
2	2	user	SQL	V2__user.sql	410358264	wust	2018-02-20 16:30:20.345122	20	t
3	3	incidence index	SQL	V3__incidence_index.sql	1300472430	wust	2018-02-20 16:30:20.372095	7	t
4	4	user revision	SQL	V4__user_revision.sql	-2009960029	wust	2018-02-20 16:30:20.387423	8	t
5	5	user implicit	SQL	V5__user_implicit.sql	-449324864	wust	2018-02-20 16:30:20.399796	5	t
6	6	user ownership	SQL	V6__user_ownership.sql	-2029942174	wust	2018-02-20 16:30:20.412844	13	t
7	6.1	put old posts into public group	SQL	V6.1__put_old_posts_into_public_group.sql	66940450	wust	2018-02-20 16:30:20.431311	1	t
8	6.2	give groupless users a personal group	SQL	V6.2__give_groupless_users_a_personal_group.sql	-492950262	wust	2018-02-20 16:30:20.438133	1	t
9	7	invite token	SQL	V7__invite_token.sql	884677346	wust	2018-02-20 16:30:20.444352	12	t
10	8	rename usergroupmember usergroupinvite connects contains	SQL	V8__rename_usergroupmember_usergroupinvite_connects_contains.sql	128024823	wust	2018-02-20 16:30:20.461644	1	t
11	9	membership userid set not null	SQL	V9__membership_userid_set_not_null.sql	503128850	wust	2018-02-20 16:30:20.467171	1	t
12	10	eliminate atoms and views	SQL	V10__eliminate_atoms_and_views.sql	-1930295764	wust	2018-02-20 16:30:20.474702	22	t
13	11	forbid self loops	SQL	V11__forbid_self_loops.sql	-1891569201	wust	2018-02-20 16:30:20.504465	2	t
14	12	post uuid	SQL	V12__post_uuid.sql	-559146598	wust	2018-02-20 16:30:20.512357	22	t
15	13	rawpost	SQL	V13__rawpost.sql	-897484842	wust	2018-02-20 16:30:20.540352	6	t
16	14	connection label	SQL	V14__connection_label.sql	679233733	wust	2018-02-20 16:30:20.552391	13	t
17	15	connection view	SQL	V15__connection_view.sql	1511843836	wust	2018-02-20 16:30:20.575564	3	t
18	16	posts author timestamp	SQL	V16__posts_author_timestamp.sql	2075479455	wust	2018-02-20 16:30:20.584944	13	t
19	17	user uuid	SQL	V17__user_uuid.sql	1922426677	wust	2018-02-20 16:30:20.603913	37	t
\.


--
-- Data for Name: user; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY "user" (id, name, revision, isimplicit) FROM stdin;
1	unknown	0	f
cjdvvfng90004vk44cnu0ml2g	anon-cjdvvfng90004vk44cnu0ml2g	0	t
\.


--
-- Data for Name: usergroup; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY usergroup (id) FROM stdin;
\.


--
-- Name: label_id_seq; Type: SEQUENCE SET; Schema: public; Owner: wust
--

SELECT pg_catalog.setval('label_id_seq', 3, true);


--
-- Name: usergroup_id_seq; Type: SEQUENCE SET; Schema: public; Owner: wust
--

SELECT pg_catalog.setval('usergroup_id_seq', 1, true);


--
-- Name: rawconnection connection_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY rawconnection
    ADD CONSTRAINT connection_pkey PRIMARY KEY (sourceid, label, targetid);


--
-- Name: label label_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY label
    ADD CONSTRAINT label_pkey PRIMARY KEY (id);


--
-- Name: ownership ownership_postid_groupid_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY ownership
    ADD CONSTRAINT ownership_postid_groupid_key UNIQUE (postid, groupid);


--
-- Name: password password_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY password
    ADD CONSTRAINT password_pkey PRIMARY KEY (id);


--
-- Name: rawpost post_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY rawpost
    ADD CONSTRAINT post_pkey PRIMARY KEY (id);


--
-- Name: schema_version schema_version_pk; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY schema_version
    ADD CONSTRAINT schema_version_pk PRIMARY KEY (installed_rank);


--
-- Name: label unique_label_name; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY label
    ADD CONSTRAINT unique_label_name UNIQUE (name);


--
-- Name: user user_name_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY "user"
    ADD CONSTRAINT user_name_key UNIQUE (name);


--
-- Name: user user_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY "user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);


--
-- Name: usergroup usergroup_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY usergroup
    ADD CONSTRAINT usergroup_pkey PRIMARY KEY (id);


--
-- Name: groupinvite usergroupinvite_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY groupinvite
    ADD CONSTRAINT usergroupinvite_pkey PRIMARY KEY (groupid);


--
-- Name: groupinvite usergroupinvite_token_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY groupinvite
    ADD CONSTRAINT usergroupinvite_token_key UNIQUE (token);


--
-- Name: membership usergroupmember_groupid_userid_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY membership
    ADD CONSTRAINT usergroupmember_groupid_userid_key UNIQUE (groupid, userid);


--
-- Name: lname; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX lname ON label USING btree (name);


--
-- Name: ownership_groupid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX ownership_groupid_idx ON ownership USING btree (groupid);


--
-- Name: ownership_postid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX ownership_postid_idx ON ownership USING btree (postid);


--
-- Name: rawpost_isdeleted_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX rawpost_isdeleted_idx ON rawpost USING btree (isdeleted);


--
-- Name: schema_version_s_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX schema_version_s_idx ON schema_version USING btree (success);


--
-- Name: usergroupinvite_token_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX usergroupinvite_token_idx ON groupinvite USING btree (token);


--
-- Name: usergroupmember_groupid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX usergroupmember_groupid_idx ON membership USING btree (groupid);


--
-- Name: usergroupmember_userid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX usergroupmember_userid_idx ON membership USING btree (userid);


--
-- Name: connection vct_delete; Type: TRIGGER; Schema: public; Owner: wust
--

CREATE TRIGGER vct_delete INSTEAD OF DELETE ON connection FOR EACH ROW EXECUTE PROCEDURE vc_delete();


--
-- Name: connection vct_insert; Type: TRIGGER; Schema: public; Owner: wust
--

CREATE TRIGGER vct_insert INSTEAD OF INSERT ON connection FOR EACH ROW EXECUTE PROCEDURE vc_insert();


--
-- Name: connection vct_update; Type: TRIGGER; Schema: public; Owner: wust
--

CREATE TRIGGER vct_update INSTEAD OF UPDATE ON connection FOR EACH ROW EXECUTE PROCEDURE vc_update();


--
-- Name: rawconnection connection_sourceid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY rawconnection
    ADD CONSTRAINT connection_sourceid_fkey FOREIGN KEY (sourceid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: rawconnection connection_targetid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY rawconnection
    ADD CONSTRAINT connection_targetid_fkey FOREIGN KEY (targetid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: ownership ownership_groupid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY ownership
    ADD CONSTRAINT ownership_groupid_fkey FOREIGN KEY (groupid) REFERENCES usergroup(id) ON DELETE CASCADE;


--
-- Name: ownership ownership_postid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY ownership
    ADD CONSTRAINT ownership_postid_fkey FOREIGN KEY (postid) REFERENCES rawpost(id) ON DELETE CASCADE;


--
-- Name: password password_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY password
    ADD CONSTRAINT password_id_fkey FOREIGN KEY (id) REFERENCES "user"(id) ON DELETE CASCADE;


--
-- Name: rawconnection rawconnection_label_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY rawconnection
    ADD CONSTRAINT rawconnection_label_fkey FOREIGN KEY (label) REFERENCES label(id) ON DELETE RESTRICT;


--
-- Name: rawpost rawpost_userid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY rawpost
    ADD CONSTRAINT rawpost_userid_fkey FOREIGN KEY (author) REFERENCES "user"(id) ON DELETE CASCADE;


--
-- Name: groupinvite usergroupinvite_groupid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY groupinvite
    ADD CONSTRAINT usergroupinvite_groupid_fkey FOREIGN KEY (groupid) REFERENCES usergroup(id) ON DELETE CASCADE;


--
-- Name: membership usergroupmember_groupid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY membership
    ADD CONSTRAINT usergroupmember_groupid_fkey FOREIGN KEY (groupid) REFERENCES usergroup(id) ON DELETE CASCADE;


--
-- Name: membership usergroupmember_userid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY membership
    ADD CONSTRAINT usergroupmember_userid_fkey FOREIGN KEY (userid) REFERENCES "user"(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

