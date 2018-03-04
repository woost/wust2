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
3	tip
4	splitFrom
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
106	100	2
107	100	2
127	100	2
cjea0bqd10003y7lra55ocagj	100	2
cjea0bzhu0004y7lrpk59715c	cjea0bk5o0001y7lri4pzxi46	2
cjea0c2s20005y7lrh3bsdhqp	cjea0bk5o0001y7lri4pzxi46	2
120	cjea0c2s20005y7lrh3bsdhqp	2
132	cjea0bzhu0004y7lrpk59715c	2
102	cjea0bqd10003y7lra55ocagj	2
121	cjea0c2s20005y7lrh3bsdhqp	2
113	cjea0bluf0002y7lreqjmzx7o	2
127	cjea0dvt10006y7lrenajn7j1	2
123	cjea0dvt10006y7lrenajn7j1	2
cjea0bk5o0001y7lri4pzxi46	100	2
119	cjea0bzhu0004y7lrpk59715c	2
115	cjea0bluf0002y7lreqjmzx7o	2
108	cjea0bqd10003y7lra55ocagj	2
cjea0bluf0002y7lreqjmzx7o	100	2
116	cjea0bluf0002y7lreqjmzx7o	2
109	100	2
111	cjea0dvt10006y7lrenajn7j1	2
128	cjea0dvt10006y7lrenajn7j1	2
131	cjea0dvt10006y7lrenajn7j1	2
126	cjea0dvt10006y7lrenajn7j1	2
125	cjea0dvt10006y7lrenajn7j1	2
112	cjea0dvt10006y7lrenajn7j1	2
cjea0z4fo0001z7kygp0m5jr8	cjea0bk5o0001y7lri4pzxi46	2
114	cjea0z4fo0001z7kygp0m5jr8	2
105	cjea0z4fo0001z7kygp0m5jr8	2
130	cjea0c2s20005y7lrh3bsdhqp	2
cjea1efkh000127kyqnacfkta	cjea0bk5o0001y7lri4pzxi46	2
110	cjea0bluf0002y7lreqjmzx7o	2
cjea1frff000227kyslubah0o	100	2
109	cjea1frff000227kyslubah0o	2
129	cjea0bluf0002y7lreqjmzx7o	2
118	cjea0bzhu0004y7lrpk59715c	2
106	cjea1frff000227kyslubah0o	2
107	cjea1frff000227kyslubah0o	2
101	cjea0c2s20005y7lrh3bsdhqp	2
124	cjea0z4fo0001z7kygp0m5jr8	2
117	cjea1efkh000127kyqnacfkta	2
130	cjea1efkh000127kyqnacfkta	2
103	cjea0z4fo0001z7kygp0m5jr8	2
114	113	3
122	cjea0bluf0002y7lreqjmzx7o	2
cjed4r9dd00043c5hsvieh1tv	cjea0bluf0002y7lreqjmzx7o	2
cjed4re7z00073c5h41hsqse1	129	4
cjed4re7z00083c5hoypq3ln0	129	4
cjed4re7z00083c5hoypq3ln0	cjea0bluf0002y7lreqjmzx7o	2
cjed4r9dd00043c5hsvieh1tv	129	4
cjed4re7z00063c5hslrzot1x	cjea0bluf0002y7lreqjmzx7o	2
cjed4re7z00063c5hslrzot1x	129	4
cjed4re7z00073c5h41hsqse1	cjea0bluf0002y7lreqjmzx7o	2
cjed4rjsc00093c5hj5r4h9kq	121	4
cjed4rjsc000a3c5h8xdoqkw5	121	4
cjed4rjsc000a3c5h8xdoqkw5	cjea0c2s20005y7lrh3bsdhqp	2
cjed4rsp7000d3c5h5pwn052t	125	4
cjed4rsp7000c3c5hoo714tj4	125	4
cjed4rsp7000b3c5h42zwkpd3	125	4
cjed4rsp7000d3c5h5pwn052t	cjea0dvt10006y7lrenajn7j1	2
cjed4rsp7000c3c5hoo714tj4	cjea0dvt10006y7lrenajn7j1	2
cjed4s8uk000e3c5h970gzswp	117	4
cjed4s8uk000f3c5hv84gwi8j	117	4
cjed4s8uk000e3c5h970gzswp	cjea1efkh000127kyqnacfkta	2
cjed5067g000m3c5hc08scgpp	cjea0z4fo0001z7kygp0m5jr8	2
cjed50kbp000r3c5hpbe4chrb	cjea0z4fo0001z7kygp0m5jr8	2
cjed50kbo000p3c5hqizxxy48	cjea0z4fo0001z7kygp0m5jr8	2
cjed5067g000m3c5hc08scgpp	103	4
cjed4zv41000k3c5hnkcryehb	103	4
cjed50kbp000q3c5hgyuz1y05	103	4
cjed4zv40000j3c5huaq64mwo	103	4
cjed50kbp000r3c5hpbe4chrb	103	4
cjed5067g000n3c5hpt8hjjkj	cjea0z4fo0001z7kygp0m5jr8	2
cjed5067g000n3c5hpt8hjjkj	103	4
cjed50kbo000p3c5hqizxxy48	103	4
cjed51rkh000z3c5h2eyr5yfj	108	4
cjed51rkh000z3c5h2eyr5yfj	cjea0bqd10003y7lra55ocagj	2
cjed51m5m000x3c5hymbk81tt	108	4
cjed51rkh00103c5h548uoz5x	cjea0bqd10003y7lra55ocagj	2
cjed51rkh00103c5h548uoz5x	108	4
cjed64nrr001d3c5hii98u0c8	101	4
cjed64nrr001e3c5haanyb2s4	101	4
cjed63swr00153c5hhhiztmr2	101	4
cjed6445c00173c5honyjo0a5	101	4
cjed6445c00173c5honyjo0a5	cjea0c2s20005y7lrh3bsdhqp	2
cjed63swr00153c5hhhiztmr2	cjea0c2s20005y7lrh3bsdhqp	2
cjed6555r001f3c5hxci56eh6	112	4
cjed6555r001h3c5hpr9bmndy	cjea0dvt10006y7lrenajn7j1	2
cjed6555r001h3c5hpr9bmndy	112	4
cjed6555r001g3c5hmov7c3qe	cjea0dvt10006y7lrenajn7j1	2
cjed6555r001f3c5hxci56eh6	cjea0dvt10006y7lrenajn7j1	2
cjed6555r001g3c5hmov7c3qe	112	4
cjed65iob001k3c5hhn74bkv8	cjea0c2s20005y7lrh3bsdhqp	2
cjed65iob001i3c5hafmht3sd	130	4
cjed65iob001j3c5h0ksh28xv	cjea1efkh000127kyqnacfkta	2
cjed65iob001k3c5hhn74bkv8	130	4
cjed65iob001j3c5h0ksh28xv	130	4
cjed66p4z001o3c5hqlc2xi4z	115	4
cjed66p4z001o3c5hqlc2xi4z	cjea0bluf0002y7lreqjmzx7o	2
cjed66p4z001q3c5hib6ks6et	cjea0bluf0002y7lreqjmzx7o	2
cjed66p4z001p3c5hbbw6dzhr	115	4
cjed66p4z001q3c5hib6ks6et	115	4
cjed675za001r3c5hqam34390	118	4
cjed675za001s3c5hn0exvcsl	118	4
cjed675za001r3c5hqam34390	cjea0bzhu0004y7lrpk59715c	2
cjed67xap001t3c5heqtk6wke	131	4
cjed67xap001v3c5hrztq9xji	cjea0dvt10006y7lrenajn7j1	2
cjed67xap001v3c5hrztq9xji	131	4
cjed67xap001u3c5hwbgoff8c	131	4
cjed68xpr00203c5h6lzkfe22	123	4
cjed68ouw001w3c5hdehcwx55	cjea0dvt10006y7lrenajn7j1	2
cjed68xpr001y3c5hg8axms82	cjea0dvt10006y7lrenajn7j1	2
cjed68ouw001w3c5hdehcwx55	123	4
cjed68xpr001z3c5hoz1eivms	123	4
cjed68xpr001y3c5hg8axms82	123	4
cjed68xpr001z3c5hoz1eivms	cjea0dvt10006y7lrenajn7j1	2
cjed698ep00213c5hpj41v6t8	116	4
cjed698ep00213c5hpj41v6t8	cjea0bluf0002y7lreqjmzx7o	2
cjed698ep00233c5h6owvah8h	116	4
cjed698ep00223c5h6hykr9yi	116	4
cjed698ep00233c5h6owvah8h	cjea0bluf0002y7lreqjmzx7o	2
cjed69j7800253c5hhzz2uu5i	cjea0bqd10003y7lra55ocagj	2
cjed69j7800263c5heve9w07o	cjea0bqd10003y7lra55ocagj	2
cjed69j7800253c5hhzz2uu5i	102	4
cjed69j7800263c5heve9w07o	102	4
cjed69j7800243c5hq1leq1ha	102	4
cjed6kxog00012v5hef525o19	100	2
cjea0dvt10006y7lrenajn7j1	cjed6kxog00012v5hef525o19	2
cjed6lqow00022v5hrn3ifux5	cjed6kxog00012v5hef525o19	2
cjed50kbp000q3c5hgyuz1y05	cjea0c2s20005y7lrh3bsdhqp	2
cjed67xap001t3c5heqtk6wke	cjed69j7800243c5hq1leq1ha	2
cjed66p4z001p3c5hbbw6dzhr	cjed6kxog00012v5hef525o19	2
cjed4rsp7000b3c5h42zwkpd3	cjea0bluf0002y7lreqjmzx7o	2
cjed68xpr00203c5h6lzkfe22	cjed69j7800243c5hq1leq1ha	2
104	cjea1efkh000127kyqnacfkta	2
cjed7ewo900012x5hxkic3z5q	cjed65iob001k3c5hhn74bkv8	4
cjed7ewo900022x5h5gol0hlg	cjed65iob001k3c5hhn74bkv8	4
cjed7ewo900012x5hxkic3z5q	cjea0c2s20005y7lrh3bsdhqp	2
cjed7ewo900022x5h5gol0hlg	cjea0c2s20005y7lrh3bsdhqp	2
cjed7f40400032x5h00x8dr7d	cjed4rjsc000a3c5h8xdoqkw5	4
cjed7f40400042x5hhcpvowg8	cjed4rjsc000a3c5h8xdoqkw5	4
cjed7f40400032x5h00x8dr7d	cjea0c2s20005y7lrh3bsdhqp	2
cjed7f40400042x5hhcpvowg8	cjea0c2s20005y7lrh3bsdhqp	2
cjed7ff2200052x5h8bd8dwha	cjed50kbp000q3c5hgyuz1y05	4
cjed7ff2200062x5hsrwcsqlr	cjed50kbp000q3c5hgyuz1y05	4
cjed7ff2200052x5h8bd8dwha	cjea0c2s20005y7lrh3bsdhqp	2
cjed7ff2200062x5hsrwcsqlr	cjea0c2s20005y7lrh3bsdhqp	2
cjed7fmxy00072x5h0ugdjjya	120	4
cjed7fmxy00082x5hfppt4y8c	120	4
cjed7fmxy00072x5h0ugdjjya	cjea0c2s20005y7lrh3bsdhqp	2
cjed7fvoe00092x5hjvxk06z6	cjed6445c00173c5honyjo0a5	4
cjed7fvoe000a2x5hj5uvm0ak	cjed6445c00173c5honyjo0a5	4
cjed7fvoe00092x5hjvxk06z6	cjea0c2s20005y7lrh3bsdhqp	2
cjed7fvoe000a2x5hj5uvm0ak	cjea0c2s20005y7lrh3bsdhqp	2
cjed7gc3z00022x5h3dufwyx5	cjed7ewo900022x5h5gol0hlg	4
cjed7gc3z00032x5hnmnf58h1	cjed7ewo900022x5h5gol0hlg	4
cjed7gc3z00012x5hnirnbrbz	cjed7ewo900022x5h5gol0hlg	4
cjed7gc3z00022x5h3dufwyx5	cjea0c2s20005y7lrh3bsdhqp	2
cjed7gc3z00032x5hnmnf58h1	cjea0c2s20005y7lrh3bsdhqp	2
cjed65iob001i3c5hafmht3sd	cjed7kymu00013c5hb925u3zy	2
cjed4zv41000k3c5hnkcryehb	cjed69j7800243c5hq1leq1ha	2
cjed51m5m000x3c5hymbk81tt	cjed69j7800243c5hq1leq1ha	2
cjed7fmxy00082x5hfppt4y8c	cjea0bk5o0001y7lri4pzxi46	2
cjed675za001s3c5hn0exvcsl	cjed7kymu00013c5hb925u3zy	2
cjed64nrr001d3c5hii98u0c8	cjea0z4fo0001z7kygp0m5jr8	2
cjed4rjsc00093c5hj5r4h9kq	cjea0bluf0002y7lreqjmzx7o	2
cjed7kymu00013c5hb925u3zy	100	2
cjed7gc3z00012x5hnirnbrbz	cjea0bk5o0001y7lri4pzxi46	2
cjed7oqri00033c5hpjl695zc	cjea0dvt10006y7lrenajn7j1	2
cjed7oqri00043c5hffj19xds	111	4
cjed7oqri00033c5hpjl695zc	111	4
cjed7oqri00043c5hffj19xds	cjea0dvt10006y7lrenajn7j1	2
cjed7oqri00023c5hxvgl1t8x	111	4
cjed7oqri00023c5hxvgl1t8x	cjea0dvt10006y7lrenajn7j1	2
cjed7pxsi00083c5hntufihx1	126	4
cjed7pxsi00093c5hu03jwpzs	cjea0dvt10006y7lrenajn7j1	2
cjed7pxsi00083c5hntufihx1	cjea0dvt10006y7lrenajn7j1	2
cjed7pt8v00053c5hiw5itpvj	cjea0dvt10006y7lrenajn7j1	2
cjed7pt8v00053c5hiw5itpvj	126	4
cjed7pxsi00073c5hdkjp75gb	cjea0dvt10006y7lrenajn7j1	2
cjed7pxsi00073c5hdkjp75gb	126	4
cjed7pxsi00093c5hu03jwpzs	126	4
cjed64nrr001e3c5haanyb2s4	100	2
cjed7t63g00013c5h8o55753v	cjed675za001r3c5hqam34390	4
cjed7t63g00023c5h509oo6t4	cjed675za001r3c5hqam34390	4
cjed7t63g00013c5h8o55753v	cjea0bzhu0004y7lrpk59715c	2
cjed7t63g00023c5h509oo6t4	cjea0bzhu0004y7lrpk59715c	2
cjed7tiy900063c5hxpusy324	119	4
cjed7tiy900073c5hlyl60lfd	119	4
cjed7tiy900063c5hxpusy324	cjea0bzhu0004y7lrpk59715c	2
cjed7tiy900073c5hlyl60lfd	cjea0bzhu0004y7lrpk59715c	2
cjed7vkhs00013a5hvimywwrh	cjed65iob001i3c5hafmht3sd	4
cjed7vkhs00023a5hjul1hq7y	cjed65iob001i3c5hafmht3sd	4
cjed7vkhs00013a5hvimywwrh	cjed7kymu00013c5hb925u3zy	2
cjed7vkhs00023a5hjul1hq7y	cjed7kymu00013c5hb925u3zy	2
cjed698ep00223c5h6hykr9yi	cjed6lqow00022v5hrn3ifux5	2
cjed67xap001u3c5hwbgoff8c	cjea0bluf0002y7lreqjmzx7o	2
\.


--
-- Data for Name: rawpost; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY rawpost (id, content, isdeleted, author, created, modified) FROM stdin;
100	Kalt duschen	f	101	2018-02-22 16:41:56.039	2018-02-22 16:41:56.039
104	Ja gut, im Sommer ist die Abkühlung einfach erfrischend. Im Winter kann man damit eben auch die Komfortzone erweitern. Und ich bin ein absoluter Morgenmuffel - nach einer eiskalten Dusche bin dann sogar ich hellwach.	f	101	2018-02-22 16:42:39.981	2018-02-22 16:42:39.981
105	Nein, ich meinte es nicht zur Erfrischung ... da ich Optimist bin, geht bei mir der "Sommer" auch von März bis Oktober\n\nIch mache es schon bewusst zur Abhärtung, nur habe ich im Winter das Problem, dass ich nach dem Duschen meine Hände nicht mehr warm kriege - eigentlich selbst nach der Sauna nicht.	f	102	2018-02-22 16:42:47.75	2018-02-22 16:42:47.75
113	Ich wollte auch mal damit anfangen, aber ich bin eine zu große Mimose. Ich kann einen abgebrochenen Kaltduschversuch vorweisen. :S	f	108	2018-02-22 16:43:35.169	2018-02-22 16:43:35.169
114	Da kann ich dir nur einen Tip geben:\n\nVersuche, dich mit aller Kraft aus der Mimosenhaftigkeit zu befreien! Das ist für mich DER Benefit aus dem Training überhaupt: nicht mehr so von äußeren Gegebenheiten abhängig zu sein.\n\nNicht auf Warmwasser angewiesen zu sein, nicht auf stärkere Menschen, nicht auf das Geld von anderen oder deren Anerkennung - das ist für mich Freiheit.\n\nAlso mach noch mal einen Versuch	f	102	2018-02-22 16:43:44.155	2018-02-22 16:43:44.155
122	Ist sicher gut für das Immunsystem, aber mir ist das zu heftig. In der Früh brauche ich da noch meine Ruhe!	f	116	2018-02-22 16:44:31.993	2018-02-22 16:44:31.993
124	Auf Highexistance gibt einen sehr guten englischsprachigen Artikel über die körperlichen und mentalen Vorteile des Kaltduschens. Neben den Goodies wie verbesserter Durchblutung, festerer Haut ist es auch Training für Disziplin und Resilienz. Ist ein besserer Start in den Tag als der Becher Kaffee, der einen unruhig und verrückt macht.	f	118	2018-02-22 16:44:54.48	2018-02-22 16:44:54.48
125	Ich wollte das auch mal. Aber ich kanns einfach nicht!\nManchmal drehe ich das Wasser am Schluss auch nochmal kalt, aber das finde ich auch so schrecklich dass ich es mich meistens nicht mehr traue	t	119	2018-02-22 16:45:00.717	2018-02-22 16:45:00.717
117	Ich dusch mich morgens auch "gern" eiskalt, aber nur weil ich mich dann einfach frischer und munterer fühle. Dass das weitere großartige Effekte hat wage ich mal zu bezweifeln	t	111	2018-02-22 16:44:01.193	2018-02-22 16:44:01.193
108	Also ich dusche mich auch kalt/warm, aber nur kalt, das schaffe ich nur im Sommer.\n\nIch stärke mein Immunsystem mit regelmäßigen Saunagängen und danach eiskalt duschen und Tauchbecken.\n\nBin auch nie verkühlt und nie krank. Durch viele Sport gibts eher die Sportverletzungen...\n\nAlso ganz so brutal muß man es sicher nicht angehen...\n\nlg\nPowerlady	t	104	2018-02-22 16:43:12.352	2018-02-22 16:43:12.352
101	Ich habe es mir zur Gewohnheit gemacht jeden Morgen kalt zu duschen. Und ja, ich frage mich jeden Morgen aufs Neue wieso ich mir das antue.\n\nAber, ich glaube, dass ich dadurch mein Immunsystem gestärkt habe. Immerhin ein halbes Jahr schon ohne Infekt oder ähnlichem. Und man trainiert seine Willensstärke. Denn wenn man sich jeden Tag kalt duscht, geht man auch eher in die Kälte raus um laufen zu gehen. Habt ihr ähnliche Erfahrungen gemacht oder seit ihr eher Warmduscher?	t	101	2018-02-22 16:42:15.067	2018-02-22 16:42:15.067
112	Hallo zwischenzeitlich mache ich das auch nach der warmen Dusche. Nur kalt alleine fände ich irgendwie nicht so toll zu mal man dann auch nicht richtig sauber wird, wenn man nur kalt duscht. Viele Grüße	t	107	2018-02-22 16:43:31.251	2018-02-22 16:43:31.251
115	Ich dusche nur warm, am besten sogar heiß  wenn ihr kalt duscht dann ist das ja ausreichend genug. Nee spaß bei seite, ich muss für mich nicht kalt duschen weil ich mein immunsystem auf andere art und weise trainiere. Es führen bekanntlich viele wege nach Rom	t	109	2018-02-22 16:43:52.264	2018-02-22 16:43:52.264
118	Ich dusche auch jedes Mal kalt! Schon wegen Blutdruck und Kreislauf!\n\nDer Trick ist nicht, sich mit den Füßen langsam an die Kälte zu gewöhnen, sondern direkt rein mit dem Kopf zuerst! Damit wirds mMn weitaus weniger schlimm	t	112	2018-02-22 16:44:09.418	2018-02-22 16:44:09.418
123	Ich bin auch eher der Warmduscher. Nach dem Training wechsele ich allerdings zwischen heiß und kalt ab, hat mir eine frühere Trainerin mal empfohlen (fragt mich aber bitte nicht warum das gut ist, das habe ich vergessen) und im Sommer muss es auch mal, wie bei den Meisten eine Abkühlung sein.	t	117	2018-02-22 16:44:37.077	2018-02-22 16:44:37.077
116	Ich mach ja vieles aber kalt duschen... Ich glaube die Wirkung wird auch etwas übertrieben, wenn man sich gesund ernährt und viel Obst isst bekommt man ebenso wenig eine Grippe.	t	110	2018-02-22 16:43:56.71	2018-02-22 16:43:56.71
102	Im Sommer ja - im Winter nur nach der Sauna!	t	102	2018-02-22 16:42:23.428	2018-02-22 16:42:23.428
110	Ich probiers auch immer wieder mit den kalten Duschen, auf Dauer krieg ich das bislang jedoch nicht hin... mal schauen wie's in zwei, drei Monaten aussieht, vielleicht fällt's mir dann leichter!	t	105	2018-02-22 16:43:21.615	2018-02-22 16:43:21.615
106	Das mit den kalten Händen kenn ich nur zu gut. Um Weihnachten herum war ich sogar im Bodensee baden. Nur ca. 1 Minute und danach gings gleich zum Kachelofen. Durch das Weihnachtsschwimmen zuvor war dass dann so richtig angenehm.	t	101	2018-02-22 16:42:56.473	2018-02-22 16:42:56.473
109	Ein Tauchbecken wäre in unserem Fitness-Studio noch genial ... Glückwunsch	t	102	2018-02-22 16:43:17.341	2018-02-22 16:43:17.341
107	Ich fahre regelmäßig im Februar mit Freunden in ein Ferienhaus nach Dänemark. Einmal muss Ostsee bei 4 Grad sein - danach aber gleich Sauna	t	102	2018-02-22 16:43:05.209	2018-02-22 16:43:05.209
127	Bin da etwas skeptisch, aber ich hab auch schon oft gehört, dass es wirklich helfen soll. Jedenfalls der schlagartige Wechsel von kalt auf heiß	t	121	2018-02-22 16:45:10.668	2018-02-22 16:45:10.668
120	Jeden Tag Kalt duschen ist effektiv auch für das Immunsystem. Mache ich immer fühle mich dadürch auch fitter.	t	114	2018-02-22 16:44:21.367	2018-02-22 16:44:21.367
111	Nach dem Duschen (warm), dusch ich mich immer noch kurz mit kaltem Wasser ab. Nur kalt duschen wäre nichts für mich	t	106	2018-02-22 16:43:25.674	2018-02-22 16:43:25.674
126	Wechselduschen sind super. Regt die Durchblutung an und soll auch gegen Cellulite helfen. Auf jeden Fall kommt der Kreislauf dadurch auf Touren.	t	120	2018-02-22 16:45:06.097	2018-02-22 16:45:06.097
119	Seh ich auch so. Zum "wach werden" bzw. für den Kreislauf ists aber top und praktiziere ich auch gerne so!	t	113	2018-02-22 16:44:14.84	2018-02-22 16:44:14.84
128	Ich mache morgens die Variante von Tim Ferriss:\n\nHeiß duschen, dann 3 min kalt über Nacken und Rücken laufen lassen. Dort sitzt das braune Körperfett, welches zu Stoffwechsel und Wärmeproduktion angeregt wird.	f	102	2018-02-22 16:45:17.671	2018-02-22 16:45:17.671
132	Morgens kalt Duschen bringt den Stoffwechsel in Schwung	f	123	2018-02-22 16:46:05.694	2018-02-22 16:46:05.694
cjea0bk5o0001y7lri4pzxi46	Pro	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:01:11.628	2018-03-02 14:01:11.628
cjea0bluf0002y7lreqjmzx7o	Kontra	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:01:13.815	2018-03-02 14:01:13.815
cjea0bqd10003y7lra55ocagj	Sauna	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:01:19.669	2018-03-02 14:01:19.669
cjea0bzhu0004y7lrpk59715c	Kreislauf	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:01:31.506	2018-03-02 14:01:31.506
cjea0c2s20005y7lrh3bsdhqp	Immunsystem	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:01:35.762	2018-03-02 14:01:35.763
cjea0dvt10006y7lrenajn7j1	Wechselduschen	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:03:00.037	2018-03-02 14:03:00.037
cjea0z4fo0001z7kygp0m5jr8	Abhärtung	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:19:30.996	2018-03-02 14:19:30.996
cjea1efkh000127kyqnacfkta	Erfrischend	f	cjea08j580000y7lrppp4l5p7	2018-03-02 14:31:25.265	2018-03-02 14:31:25.265
cjea1frff000227kyslubah0o	delete	t	cjea08j580000y7lrppp4l5p7	2018-03-02 14:32:27.291	2018-03-02 14:32:27.291
cjed4re7z00073c5h41hsqse1	Setze mich oft genug der kälte aus wegen dem Training für Hindernisläufe, wenn man da auf das Eiswasserbecken oder auf eine Artik Area zu läuft ist mein Kreislauf ganz oben.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:28:47.423	2018-03-04 18:28:47.423
129	Ob das wirklich etwas bringt muß jeder für sich selbst entscheiden. Das man davon wach wird aufjedenfall, ich bin ein absoluter Warmduscher. Setze mich oft genug der kälte aus wegen dem Training für Hindernisläufe, wenn man da auf das Eiswasserbecken oder auf eine Artik Area zu läuft ist mein Kreislauf ganz oben.	t	122	2018-02-22 16:45:21.963	2018-02-22 16:45:21.963
cjed4rjsc00093c5hj5r4h9kq	Da muss man ziemlich schmerzfrei sein und man gewöhnt sich auch nach langer Zeit nicht daran. Ich spreche aus Erfahrung .	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:28:54.636	2018-03-04 18:28:54.636
121	Da muss man ziemlich schmerzfrei sein und man gewöhnt sich auch nach langer Zeit nicht daran. Ich spreche aus Erfahrung .\n\nEs soll aber gut sein für das Immunsystem. Vielleicht ist es aber auch nur der Glaube daran, der hilft.	t	115	2018-02-22 16:44:27.553	2018-02-22 16:44:27.553
cjed4rsp7000b3c5h42zwkpd3	Ich wollte das auch mal. Aber ich kanns einfach nicht!	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:29:06.187	2018-03-04 18:29:06.187
cjed4rsp7000c3c5hoo714tj4	Manchmal drehe ich das Wasser am Schluss auch nochmal kalt, aber das finde ich auch so schrecklich dass ich es mich meistens nicht mehr traue	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:29:06.187	2018-03-04 18:29:06.187
cjed4s8uk000e3c5h970gzswp	Ich dusch mich morgens auch "gern" eiskalt, aber nur weil ich mich dann einfach frischer und munterer fühle.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:29:27.116	2018-03-04 18:29:27.116
cjed4zv41000k3c5hnkcryehb	In den Sommermonaten bei Temperaturen von >30° muss es dann aber schon mal eine eiskalte Dusche sein.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:35:22.561	2018-03-04 18:35:22.561
cjed50kbo000p3c5hqizxxy48	Für die Abhärtung wird es wohl auch ganz gut sein. Kälte ist ja im Grunde - genauso wie extreme Hitze - eine Art Schmerz, schlägt also auf den Schmerzrezeptoren auf. Doch eben, die Schmerzgrenzen kann man verschieben, wenn man sich entsprechend trainiert. Deswegen glaube ich schon, dass Kaltduschen die eisigen Temperaturen erträglicher macht	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:35:55.236	2018-03-04 18:35:55.236
cjed5067g000n3c5hpt8hjjkj	Zumindest was die Willensstärke angeht, mache ich immer wieder Erfahrungen wie du. Selbst im Hochsommer braucht Kaltduschen Überwindung.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:35:36.94	2018-03-04 18:35:36.94
130	Ich mache es auch schon einen Monat lang. Am Anfang war es super schwer, habe nur mit den Füßen angefangen und dann immer mehr nach oben Man fühlt sich wirklich erfrischt und erholt danach. Es stärk das Immunsystem und ist auch gut gegen Stress. Empfehlenswert!	t	125	2018-02-22 16:45:27.812	2018-02-22 16:45:27.812
131	Ich persönlich bin eher ein Warm- bzw. Heißduscher... vorallem in den Wintermonaten\nAllerdings hab ich so oft das problem, dass ich nicht richtig in schwung komme. Für mich wäre es das schlimmste mich morgens direkt unter eine kalte Dusche stellen zu müssen. Ich dusche daher immer warm und zum Schluss drehe ich die Temperatur dann etwas kälter	t	124	2018-02-22 16:45:57.18	2018-02-22 16:45:57.18
cjed50kbp000r3c5hpbe4chrb		t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:35:55.237	2018-03-04 18:35:55.237
cjed4rsp7000d3c5h5pwn052t		t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:29:06.187	2018-03-04 18:29:06.187
cjed4re7z00083c5hoypq3ln0		t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:28:47.424	2018-03-04 18:28:47.424
cjed4re7z00063c5hslrzot1x	Das man davon wach wird aufjedenfall, ich bin ein absoluter Warmduscher.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:28:47.423	2018-03-04 18:28:47.423
cjed4r9dd00043c5hsvieh1tv	Ob das wirklich etwas bringt muß jeder für sich selbst entscheiden.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:28:41.137	2018-03-04 18:28:41.137
cjed5067g000m3c5hc08scgpp	Insofern als dass mir die Ganzjahreserfahrung fehlt, kann ich deiner Frage / Aussage nur bis zu einer gewissen Grenze zuverlässig antworten.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:35:36.94	2018-03-04 18:35:36.94
cjed4s8uk000f3c5hv84gwi8j	Dass das weitere großartige Effekte hat wage ich mal zu bezweifeln	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:29:27.116	2018-03-04 18:29:27.116
cjed4rjsc000a3c5h8xdoqkw5	Es soll aber gut sein für das Immunsystem. Vielleicht ist es aber auch nur der Glaube daran, der hilft.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:28:54.636	2018-03-04 18:28:54.636
cjed50kbp000q3c5hgyuz1y05	Eine andere Frage ist, ob das Immunsystem gestärkt wird. Ich vermute schon. Doch einen Beweis habe ich dafür keinen. Vielleicht gibt es im Netz irgendwo Studien dazu	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:35:55.237	2018-03-04 18:35:55.237
cjed7fmxy00072x5h0ugdjjya	Jeden Tag Kalt duschen ist effektiv auch für das Immunsystem.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:37.702	2018-03-04 19:43:37.702
cjed7fmxy00082x5hfppt4y8c	Mache ich immer fühle mich dadürch auch fitter.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:37.702	2018-03-04 19:43:37.702
cjed7fvoe000a2x5hj5uvm0ak	Immerhin ein halbes Jahr schon ohne Infekt oder ähnlichem.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:49.022	2018-03-04 19:43:49.022
cjed6445c00173c5honyjo0a5	Aber, ich glaube, dass ich dadurch mein Immunsystem gestärkt habe. Immerhin ein halbes Jahr schon ohne Infekt oder ähnlichem.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:06:40.513	2018-03-04 19:06:40.513
cjed7gc3z00012x5hnirnbrbz	auch gut gegen Stress	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:44:10.319	2018-03-04 19:44:10.319
cjed7gc3z00022x5h3dufwyx5	. Empfehlenswert!	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:44:10.319	2018-03-04 19:44:10.319
cjed7gc3z00032x5hnmnf58h1		t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:44:10.319	2018-03-04 19:44:10.319
cjed7fvoe00092x5hjvxk06z6	Aber, ich glaube, dass ich dadurch mein Immunsystem gestärkt habe.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:49.022	2018-03-04 19:43:49.022
cjed4zv40000j3c5huaq64mwo	Jetzt um diese Jahreszeit dusche ich warm und nur warm.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:35:22.56	2018-03-04 18:35:22.56
cjed7oqri00033c5hpjl695zc	Nur kalt duschen wäre nichts für mich	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:50:42.558	2018-03-04 19:50:42.558
cjed7oqri00043c5hffj19xds		t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:50:42.558	2018-03-04 19:50:42.558
103	Jetzt um diese Jahreszeit dusche ich warm und nur warm.\nIn den Sommermonaten bei Temperaturen von >30° muss es dann aber schon mal eine eiskalte Dusche sein.\nInsofern als dass mir die Ganzjahreserfahrung fehlt, kann ich deiner Frage / Aussage nur bis zu einer gewissen Grenze zuverlässig antworten. Zumindest was die Willensstärke angeht, mache ich immer wieder Erfahrungen wie du. Selbst im Hochsommer braucht Kaltduschen Überwindung.\n\nFür die Abhärtung wird es wohl auch ganz gut sein. Kälte ist ja im Grunde - genauso wie extreme Hitze - eine Art Schmerz, schlägt also auf den Schmerzrezeptoren auf. Doch eben, die Schmerzgrenzen kann man verschieben, wenn man sich entsprechend trainiert. Deswegen glaube ich schon, dass Kaltduschen die eisigen Temperaturen erträglicher macht\n\nEine andere Frage ist, ob das Immunsystem gestärkt wird. Ich vermute schon. Doch einen Beweis habe ich dafür keinen. Vielleicht gibt es im Netz irgendwo Studien dazu	t	103	2018-02-22 16:42:33.889	2018-02-22 16:42:33.889
cjed51m5m000x3c5hymbk81tt	Also ich dusche mich auch kalt/warm, aber nur kalt, das schaffe ich nur im Sommer.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:36:44.266	2018-03-04 18:36:44.266
cjed51rkh000z3c5h2eyr5yfj	Ich stärke mein Immunsystem mit regelmäßigen Saunagängen und danach eiskalt duschen und Tauchbecken.\nBin auch nie verkühlt und nie krank.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 18:36:51.281	2018-03-04 18:36:51.281
cjed64nrr001d3c5hii98u0c8	Und man trainiert seine Willensstärke. Denn wenn man sich jeden Tag kalt duscht, geht man auch eher in die Kälte raus um laufen zu gehen.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:05.943	2018-03-04 19:07:05.943
cjed64nrr001e3c5haanyb2s4	Habt ihr ähnliche Erfahrungen gemacht oder seit ihr eher Warmduscher?	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:05.943	2018-03-04 19:07:05.943
cjed6555r001g3c5hmov7c3qe	Nur kalt alleine fände ich irgendwie nicht so toll zu mal man dann auch nicht richtig sauber wird, wenn man nur kalt duscht.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:28.479	2018-03-04 19:07:28.479
cjed65iob001j3c5h0ksh28xv	Man fühlt sich wirklich erfrischt und erholt danach.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:45.995	2018-03-04 19:07:45.995
cjed66p4z001p3c5hbbw6dzhr	Nee spaß bei seite, ich muss für mich nicht kalt duschen weil ich mein immunsystem auf andere art und weise trainiere.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:08:41.027	2018-03-04 19:08:41.027
cjed675za001s3c5hn0exvcsl	Der Trick ist nicht, sich mit den Füßen langsam an die Kälte zu gewöhnen, sondern direkt rein mit dem Kopf zuerst! Damit wirds mMn weitaus weniger schlimm	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:09:02.854	2018-03-04 19:09:02.854
cjed67xap001t3c5heqtk6wke	Ich persönlich bin eher ein Warm- bzw. Heißduscher... vorallem in den Wintermonaten	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:09:38.257	2018-03-04 19:09:38.257
cjed67xap001u3c5hwbgoff8c	Allerdings hab ich so oft das problem, dass ich nicht richtig in schwung komme. Für mich wäre es das schlimmste mich morgens direkt unter eine kalte Dusche stellen zu müssen.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:09:38.257	2018-03-04 19:09:38.257
cjed68xpr001y3c5hg8axms82	Nach dem Training wechsele ich allerdings zwischen heiß und kalt ab, hat mir eine frühere Trainerin mal empfohlen (fragt mich aber bitte nicht warum das gut ist, das habe ich vergessen)	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:25.455	2018-03-04 19:10:25.455
cjed68xpr00203c5h6lzkfe22	im Sommer muss es auch mal, wie bei den Meisten eine Abkühlung sein.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:25.455	2018-03-04 19:10:25.455
cjed698ep00223c5h6hykr9yi	Ich glaube die Wirkung wird auch etwas übertrieben, wenn man sich gesund ernährt und viel Obst isst bekommt man ebenso wenig eine Grippe.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:39.313	2018-03-04 19:10:39.313
cjed69j7800243c5hq1leq1ha	Im Sommer ja -	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:53.3	2018-03-04 19:10:53.3
cjed69j7800253c5hhzz2uu5i	im Winter nur nach der Sauna	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:53.3	2018-03-04 19:10:53.3
cjed51rkh00103c5h548uoz5x	Durch viele Sport gibts eher die Sportverletzungen...\nAlso ganz so brutal muß man es sicher nicht angehen...\nlg\nPowerlady	t	cjed40xwt0000315hkxglaj3w	2018-03-04 18:36:51.281	2018-03-04 18:36:51.281
cjed6555r001h3c5hpr9bmndy	Viele Grüße	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:28.479	2018-03-04 19:07:28.479
cjed698ep00233c5h6owvah8h		t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:39.313	2018-03-04 19:10:39.313
cjed68xpr001z3c5hoz1eivms	und	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:25.455	2018-03-04 19:10:25.455
cjed69j7800263c5heve9w07o	!	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:53.3	2018-03-04 19:10:53.3
cjed6kxog00012v5hef525o19	Alternative	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:19:45.28	2018-03-04 19:19:45.28
cjed6lqow00022v5hrn3ifux5	Ernährung	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:20:22.88	2018-03-04 19:20:22.88
cjed68ouw001w3c5hdehcwx55	Ich bin auch eher der Warmduscher.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:13.976	2018-03-04 19:10:13.976
cjed66p4z001q3c5hib6ks6et	Es führen bekanntlich viele wege nach Rom	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:08:41.027	2018-03-04 19:08:41.027
cjed698ep00213c5hpj41v6t8	Ich mach ja vieles aber kalt duschen...	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:10:39.313	2018-03-04 19:10:39.313
cjed66p4z001o3c5hqlc2xi4z	Ich dusche nur warm, am besten sogar heiß  wenn ihr kalt duscht dann ist das ja ausreichend genug.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:08:41.027	2018-03-04 19:08:41.027
cjed6555r001f3c5hxci56eh6	Hallo zwischenzeitlich mache ich das auch nach der warmen Dusche.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:28.479	2018-03-04 19:07:28.479
cjed67xap001v3c5hrztq9xji	Ich dusche daher immer warm und zum Schluss drehe ich die Temperatur dann etwas kälter	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:09:38.257	2018-03-04 19:09:38.257
cjed63swr00153c5hhhiztmr2	Ich habe es mir zur Gewohnheit gemacht jeden Morgen kalt zu duschen. Und ja, ich frage mich jeden Morgen aufs Neue wieso ich mir das antue.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:06:25.947	2018-03-04 19:06:25.947
cjed65iob001k3c5hhn74bkv8	Es stärk das Immunsystem und ist auch gut gegen Stress. Empfehlenswert!	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:45.995	2018-03-04 19:07:45.995
cjed7f40400042x5hhcpvowg8	Vielleicht ist es aber auch nur der Glaube daran, der hilft.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:13.157	2018-03-04 19:43:13.157
cjed7ff2200062x5hsrwcsqlr	Beweis habe ich dafür keinen. Vielleicht gibt es im Netz irgendwo Studien dazu	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:27.482	2018-03-04 19:43:27.482
cjed7ewo900022x5h5gol0hlg	auch gut gegen Stress. Empfehlenswert!	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:03.657	2018-03-04 19:43:03.657
cjed7ewo900012x5hxkic3z5q	Es stärk das Immunsystem und ist	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:03.657	2018-03-04 19:43:03.657
cjed7f40400032x5h00x8dr7d	Es soll aber gut sein für das Immunsystem.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:13.156	2018-03-04 19:43:13.156
cjed7ff2200052x5h8bd8dwha	Eine andere Frage ist, ob das Immunsystem gestärkt wird. Ich vermute schon. Doch einen	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:43:27.482	2018-03-04 19:43:27.482
cjed7kymu00013c5hb925u3zy	Tip	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:47:46.134	2018-03-04 19:47:46.134
cjed675za001r3c5hqam34390	Ich dusche auch jedes Mal kalt! Schon wegen Blutdruck und Kreislauf!	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:09:02.854	2018-03-04 19:09:02.854
cjed7oqri00023c5hxvgl1t8x	Nach dem Duschen (warm), dusch ich mich immer noch kurz mit kaltem Wasser ab.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:50:42.558	2018-03-04 19:50:42.558
cjed7pxsi00073c5hdkjp75gb	Regt die Durchblutung an und soll auch gegen Cellulite helfen.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:51:38.322	2018-03-04 19:51:38.322
cjed7pxsi00083c5hntufihx1	Auf jeden Fall kommt der Kreislauf dadurch auf Touren.	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:51:38.322	2018-03-04 19:51:38.322
cjed7pxsi00093c5hu03jwpzs		t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:51:38.322	2018-03-04 19:51:38.322
cjed7pt8v00053c5hiw5itpvj	Wechselduschen sind super.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:51:32.431	2018-03-04 19:51:32.431
cjed7t63g00023c5h509oo6t4	Schon wegen Blutdruck und Kreislauf!	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:54:09.052	2018-03-04 19:54:09.052
cjed7tiy900073c5hlyl60lfd	Zum "wach werden" bzw. für den Kreislauf ists aber top und praktiziere ich auch gerne so!	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:54:25.713	2018-03-04 19:54:25.713
cjed7tiy900063c5hxpusy324	Seh ich auch so.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:54:25.713	2018-03-04 19:54:25.713
cjed7t63g00013c5h8o55753v	Ich dusche auch jedes Mal kalt!	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:54:09.052	2018-03-04 19:54:09.052
cjed7vkhs00023a5hjul1hq7y	Am Anfang war es super schwer, habe nur mit den Füßen angefangen und dann immer mehr nach oben	f	cjed40xwt0000315hkxglaj3w	2018-03-04 19:56:01.024	2018-03-04 19:56:01.024
cjed65iob001i3c5hafmht3sd	Ich mache es auch schon einen Monat lang. Am Anfang war es super schwer, habe nur mit den Füßen angefangen und dann immer mehr nach oben	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:07:45.995	2018-03-04 19:07:45.995
cjed7vkhs00013a5hvimywwrh	Ich mache es auch schon einen Monat lang.	t	cjed40xwt0000315hkxglaj3w	2018-03-04 19:56:01.024	2018-03-04 19:56:01.024
\.


--
-- Data for Name: schema_version; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY schema_version (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	graph	SQL	V1__graph.sql	422010744	wust	2018-02-22 15:30:22.322044	482	t
2	2	user	SQL	V2__user.sql	410358264	wust	2018-02-22 15:30:22.917825	336	t
3	3	incidence index	SQL	V3__incidence_index.sql	1300472430	wust	2018-02-22 15:30:23.337625	116	t
4	4	user revision	SQL	V4__user_revision.sql	-2009960029	wust	2018-02-22 15:30:23.515287	207	t
5	5	user implicit	SQL	V5__user_implicit.sql	-449324864	wust	2018-02-22 15:30:23.794067	227	t
6	6	user ownership	SQL	V6__user_ownership.sql	-2029942174	wust	2018-02-22 15:30:24.092162	468	t
7	6.1	put old posts into public group	SQL	V6.1__put_old_posts_into_public_group.sql	66940450	wust	2018-02-22 15:30:24.615794	2	t
8	6.2	give groupless users a personal group	SQL	V6.2__give_groupless_users_a_personal_group.sql	-492950262	wust	2018-02-22 15:30:24.682109	4	t
9	7	invite token	SQL	V7__invite_token.sql	884677346	wust	2018-02-22 15:30:24.747713	285	t
10	8	rename usergroupmember usergroupinvite connects contains	SQL	V8__rename_usergroupmember_usergroupinvite_connects_contains.sql	128024823	wust	2018-02-22 15:30:25.098639	3	t
11	9	membership userid set not null	SQL	V9__membership_userid_set_not_null.sql	503128850	wust	2018-02-22 15:30:25.182015	10	t
12	10	eliminate atoms and views	SQL	V10__eliminate_atoms_and_views.sql	-1930295764	wust	2018-02-22 15:30:25.25094	369	t
13	11	forbid self loops	SQL	V11__forbid_self_loops.sql	-1891569201	wust	2018-02-22 15:30:25.697663	3	t
14	12	post uuid	SQL	V12__post_uuid.sql	-559146598	wust	2018-02-22 15:30:25.784914	521	t
15	13	rawpost	SQL	V13__rawpost.sql	-897484842	wust	2018-02-22 15:30:26.364904	248	t
16	14	connection label	SQL	V14__connection_label.sql	679233733	wust	2018-02-22 15:30:26.673972	475	t
17	15	connection view	SQL	V15__connection_view.sql	1511843836	wust	2018-02-22 15:30:27.272633	24	t
18	16	posts author timestamp	SQL	V16__posts_author_timestamp.sql	2075479455	wust	2018-02-22 15:30:27.378775	217	t
19	17	user uuid	SQL	V17__user_uuid.sql	1922426677	wust	2018-02-22 15:30:27.67542	968	t
\.


--
-- Data for Name: user; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY "user" (id, name, revision, isimplicit) FROM stdin;
1	unknown	0	f
101	Mr. Marathon	0	t
102	Das Experiment	0	f
103	Rik	0	f
104	IngridPowerlady	0	f
105	Gelbes_Schaf	0	f
106	Lauredana	0	f
107	Peter201082	0	f
108	Unixx	0	f
109	fitnessandrea	0	f
110	Michaela-Bln	0	f
111	Monica123	0	f
112	FFNiko	0	f
113	Buddy58	0	f
114	gemix.2	0	f
115	Healthfan	0	f
116	mario_schwarz	0	f
117	KemperMo	0	f
118	KJJ	0	f
119	marina2	0	f
120	Bella1981	0	f
121	Bench4Life	0	f
122	FitterFit	0	f
123	TheBeast	0	f
124	fitness4life	0	f
125	SportuhrenNerd	0	f
cjea08j580000y7lrppp4l5p7	anon-cjea08j580000y7lrppp4l5p7	0	t
cjea2ptok0000z7l2xbwe6czl	anon-cjea2ptok0000z7l2xbwe6czl	0	t
cjecspfuh0000u7lq9u180j75	anon-cjecspfuh0000u7lq9u180j75	0	t
cjed40xwt0000315hkxglaj3w	anon-cjed40xwt0000315hkxglaj3w	0	t
\.


--
-- Data for Name: usergroup; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY usergroup (id) FROM stdin;
\.


--
-- Name: label_id_seq; Type: SEQUENCE SET; Schema: public; Owner: wust
--

SELECT pg_catalog.setval('label_id_seq', 4, true);


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

