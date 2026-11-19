// Runs the actual schema and migration in isolated PostgreSQL/WASM; no remote data.
// deno test --config supabase/tests/deno.json --allow-read --allow-env supabase/tests/multidispositivo_test.ts
import { PGlite } from "npm:@electric-sql/pglite@0.3.9";
import { strict as assert } from "node:assert";

Deno.test("sync is atomic, scoped, resumable, paginated and idempotent", async () => {
  const db = new PGlite();
  const user = crypto.randomUUID();
  const otherUser = crypto.randomUUID();
  const child = crypto.randomUUID();
  const sibling = crypto.randomUUID();
  const foreignChild = crypto.randomUUID();
  const version = crypto.randomUUID();
  const session = crypto.randomUUID();
  const now = 1_800_000_000_000;
  try {
    await db.exec(`
      create role anon; create role authenticated;
      create schema auth; create table auth.users(id uuid primary key);
      create function auth.uid() returns uuid language sql as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    `);
    const schema = await Deno.readTextFile(new URL("../migrations/20260930160000_kmat_schema.sql", import.meta.url));
    await db.exec(schema.replace("create extension if not exists pgcrypto;", ""));
    await db.exec(await Deno.readTextFile(new URL("../migrations/20261106001000_sincronizacion_multidispositivo.sql", import.meta.url)));
    await db.exec(`
      insert into auth.users values ('${user}'), ('${otherUser}');
      insert into public.perfil(id_usuario, nombre_mostrado) values ('${user}', 'A'), ('${otherUser}', 'B');
      insert into public.usuario_rol(id_usuario,codigo_rol) values ('${user}','RESPONSABLE'), ('${otherUser}','RESPONSABLE');
      insert into public.estudiante(id_estudiante,nombre_mostrado,codigo_nivel_educativo)
        values ('${child}','child','BASICO_1'), ('${sibling}','sibling','BASICO_1'), ('${foreignChild}','foreign','BASICO_1');
      insert into public.responsable_estudiante(id_estudiante,id_responsable)
        values ('${child}','${user}'), ('${sibling}','${user}'), ('${foreignChild}','${otherUser}');
      insert into public.nivel_aprendizaje(codigo,nombre,orden) values ('test','test',1);
      insert into public.contenido(codigo,nombre,orden) values ('test','test',1);
      insert into public.ejercicio(codigo) values ('test');
      insert into public.ejercicio_version(id_ejercicio_version,id_ejercicio,numero_version,id_contenido,id_nivel,
        tipo_ejercicio,tipo_respuesta,enunciado,solucion,dificultad,estado)
      select '${version}',e.id_ejercicio,1,c.id_contenido,n.id_nivel,'SUMA','MANUSCRITA','1 + 1','2',1,'PUBLICADO'
      from public.ejercicio e cross join public.contenido c cross join public.nivel_aprendizaje n;
    `);
    await db.query("select set_config('request.jwt.claim.sub', $1, false)", [user]);
    // Invoke RPCs with the same database role used by authenticated requests.
    await db.exec("set role authenticated");
    const event = (operation: string, payload: Record<string, unknown> = {}) => ({
      id: crypto.randomUUID(), operation,
      payload: { student_local_id: child, local_session_id: session, ...payload },
    });
    const upload = (events: unknown[]) => db.query("select public.sincronizar_aprendizaje($1::jsonb)", [JSON.stringify(events)]);
    const read = async (student = child, after: string | null = null) => {
      const r = await db.query<{ data: { sessions: Array<Record<string, unknown>> } }>(
        "select public.descargar_aprendizaje($1::uuid,$2::uuid) as data", [student, after]);
      return r.rows[0].data.sessions;
    };
    const started = event("SESSION_STARTED", { session_type: "DIAGNOSTICO", started_at: now });
    const exercise = { id: "offline-sum-1-1", content: "Sumas", statement: "1+1", expectedAnswer: "2", difficulty: 0 };
    const plan = event("SESSION_PLANNED", { exercises: [exercise] });
    const attemptId = crypto.randomUUID();
    const attempt = event("ATTEMPT_RECORDED", {
      local_attempt_id: attemptId, exercise_local_id: exercise.id, exercise_version_remote_id: version,
      attempt_number: 1, answer: "2", is_correct: true, elapsed_millis: 1000, recorded_at: now + 1000,
      exercise, strokes: [],
    });
    await upload([started, plan, attempt]);
    await upload([started, plan, attempt]);
    let snapshot = (await read())[0];
    assert.equal((snapshot.attempts as unknown[]).length, 1);
    assert.deepEqual(snapshot.plan, [exercise]);
    assert.equal((await read(sibling)).length, 0);
    await assert.rejects(() => read(foreignChild));
    await assert.rejects(() => upload([event("SESSION_STARTED", { student_local_id: foreignChild, local_session_id: crypto.randomUUID(), started_at: now })]));
    await assert.rejects(() => upload([
      event("SESSION_COMPLETED", { ended_at: now + 2000 }),
      event("INVALID_OPERATION"),
    ]));
    assert.equal((await read())[0].status, "ACTIVE", "failed batches must roll back all changes");
    const report = { overallLevel: 2, questionCount: 10, completedAt: now + 2000, axes: Array(5).fill({ level: 2 }) };
    await upload([event("SESSION_COMPLETED", { ended_at: now + 2000 }), event("DIAGNOSTIC_RECORDED", { report })]);
    await upload([started, event("SESSION_PLANNED", { exercises: [{ ...exercise, id: "replacement" }] })]);
    snapshot = (await read())[0];
    assert.equal(snapshot.status, "COMPLETED", "replay must never reopen completed sessions");
    assert.deepEqual(snapshot.report, report);
    assert.deepEqual(snapshot.plan, [exercise], "first plan is retained");
    // Two online devices reserve the same next practice session.
    const claim = async () => (await db.query<{ data: { session_id: string } }>(
      "select public.retomar_sesion_aprendizaje($1,'PRACTICA',$2,$3) as data", [child, crypto.randomUUID(), now + 3000]
    )).rows[0].data.session_id;
    assert.equal(await claim(), await claim());
    for (let i = 0; i < 55; i++) {
      await upload([event("SESSION_STARTED", { local_session_id: crypto.randomUUID(), session_type: "PRACTICA", started_at: now + i })]);
    }
    const firstPage = await read();
    const secondPage = await read(child, firstPage.at(-1)!.id as string);
    assert.equal(firstPage.length, 50);
    assert.equal(secondPage.length, 7);
    assert.equal(new Set([...firstPage, ...secondPage].map(s => s.id)).size, 57);
    await db.query("select set_config('request.jwt.claim.sub', $1, false)", [otherUser]);
    await assert.rejects(() => read(child));
  } finally { await db.close(); }
});
