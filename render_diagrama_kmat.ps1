Add-Type -AssemblyName System.Drawing

$bitmap = [System.Drawing.Bitmap]::new(1800, 1260)
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::FromArgb(245,248,250))

$fontTitle = [System.Drawing.Font]::new('Arial', 29, [System.Drawing.FontStyle]::Bold)
$fontHead = [System.Drawing.Font]::new('Arial', 15, [System.Drawing.FontStyle]::Bold)
$fontField = [System.Drawing.Font]::new('Consolas', 13)
$fontPk = [System.Drawing.Font]::new('Consolas', 13, [System.Drawing.FontStyle]::Bold)
$fontNote = [System.Drawing.Font]::new('Arial', 13)
$brushTitle = [System.Drawing.Brushes]::DarkSlateGray
$brushField = [System.Drawing.Brushes]::DarkSlateGray
$brushWhite = [System.Drawing.Brushes]::White
$brushNote = [System.Drawing.Brushes]::SlateGray
$linePen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(130,153,170), 2)
$dashPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(130,153,170), 2)
$dashPen.DashPattern = [single[]](7,5)

function Line([int]$x1,[int]$y1,[int]$x2,[int]$y2,[bool]$dashed=$false) {
  if ($dashed) { $g.DrawLine($dashPen,$x1,$y1,$x2,$y2) } else { $g.DrawLine($linePen,$x1,$y1,$x2,$y2) }
}
function Card([int]$x,[int]$y,[int]$w,[int]$h,[System.Drawing.Color]$color,[string]$title,[string[]]$lines) {
  $rect = [System.Drawing.Rectangle]::new($x,$y,$w,$h)
  $g.FillRectangle([System.Drawing.Brushes]::White,$rect)
  $g.DrawRectangle([System.Drawing.Pens]::LightSteelBlue,$rect)
  $headerBrush = [System.Drawing.SolidBrush]::new($color)
  $g.FillRectangle($headerBrush,$x,$y,$w,30)
  $headerBrush.Dispose()
  $g.DrawString($title,$fontHead,$brushWhite,$x+14,$y+6)
  for ($i=0; $i -lt $lines.Length; $i++) {
    $f = if ($i -eq 0) { $fontPk } else { $fontField }
    $g.DrawString($lines[$i],$f,$brushField,$x+14,$y+40+($i*20))
  }
}

$blue=[System.Drawing.Color]::FromArgb(40,93,124); $teal=[System.Drawing.Color]::FromArgb(61,115,142)
$violet=[System.Drawing.Color]::FromArgb(107,105,142); $purple=[System.Drawing.Color]::FromArgb(123,99,141)
$orange=[System.Drawing.Color]::FromArgb(166,109,73); $green=[System.Drawing.Color]::FromArgb(80,123,88)
$red=[System.Drawing.Color]::FromArgb(176,93,85); $rose=[System.Drawing.Color]::FromArgb(134,94,120)

$g.DrawString('K-MAT IA · Diagrama relacional mejorado (MVP)',$fontTitle,$brushTitle,60,25)
$g.DrawString('Relaciones principales · PK = clave primaria · FK = clave foránea · línea punteada = relación opcional o auditoría',$fontNote,$brushNote,60,62)

# Relaciones, dibujadas antes de las tablas.
Line 300 206 345 206; Line 300 206 300 397; Line 300 397 345 397; Line 300 206 300 660; Line 300 660 345 660
Line 615 206 665 206; Line 777 250 777 316; Line 1090 234 1090 316; Line 1110 206 1170 206
Line 890 442 890 550; Line 890 550 955 550; Line 890 568 890 602
Line 615 660 680 660; Line 615 720 615 730; Line 615 730 955 730; Line 1085 602 1085 566; Line 1085 566 1350 566; Line 1350 566 1350 602
Line 1245 676 1170 676; Line 1245 748 1170 748; Line 1245 748 1245 920; Line 1245 920 1170 920
Line 1415 730 1470 730; Line 1595 874 1595 920; Line 1595 920 1415 920
Line 615 720 615 1040; Line 615 1040 1170 1040; Line 1415 802 1415 1040
Line 190 272 190 110 $true; Line 190 110 1600 110 $true; Line 1600 110 1600 160 $true

Card 60 160 240 112 $blue 'USUARIO' @('PK  id_usuario','FK  auth_user_id','rol · correo · estado')
Card 345 160 270 100 $blue 'GRUPO' @('PK  id_grupo','FK  id_docente','nombre · código · estado')
Card 665 160 225 90 $teal 'INSCRIPCION_GRUPO' @('PK  id_inscripción','FK grupo · estudiante')
Card 955 160 155 74 $violet 'NIVEL' @('PK id_nivel')
Card 1170 160 250 74 $violet 'CONTENIDO' @('PK id_contenido · NT1/NT2')
Card 1470 160 245 90 $purple 'AUDITORIA' @('PK id_auditoría','FK usuario · entidad · acción')
Card 345 316 270 126 $teal 'ESTUDIANTE' @('PK  id_estudiante','FK responsable · nivel_actual','nombre · avatar · nivel_educativo','estado · fechas')
Card 760 316 260 126 $orange 'EJERCICIO' @('PK  id_ejercicio','FK contenido · nivel','versión · tipo · dificultad','respuesta · configuración')
Card 1170 316 250 126 $orange 'RECURSO_APOYO' @('PK  id_recurso','FK contenido','tipo · etapa_apoyo','uri · disponible_offline')
Card 345 602 270 118 $green 'SESION_APRENDIZAJE' @('PK  id_sesión','FK estudiante','tipo · inicio · fin · estado')
Card 680 602 260 118 $green 'PROGRESO_CONTENIDO' @('PK estudiante + contenido','FK estudiante · contenido','nivel_dominio · versión')
Card 955 602 290 146 $orange 'ASIGNACION_EJERCICIO' @('PK  id_asignación','FK estudiante · ejercicio · sesión','FK refuerzo (opcional)','origen · prioridad · estado','versión_ejercicio · fechas')
Card 1350 602 260 112 $orange 'REFUERZO_DOCENTE' @('PK  id_refuerzo','FK docente · estudiante','contenido · recurso · estado')
Card 1170 730 245 102 $red 'INTENTO_EJERCICIO' @('PK  id_intento','FK asignación','respuesta · correcto · tiempo')
Card 1470 730 250 144 $red 'DETECCION_DIFICULTAD' @('PK  id_detección','FK asignación','tipo · severidad · estado','errores · intentos · tiempo','fecha_resolución')
Card 1170 920 245 126 $rose 'INTERVENCION_APOYO' @('PK  id_intervención','FK estudiante · recurso','FK intento o detección','origen · resultado · fecha')
Card 1470 994 245 82 $teal 'CONSENTIMIENTO' @('PK id_consentimiento','FK estudiante · responsable')

$g.FillRectangle([System.Drawing.Brushes]::AliceBlue,60,1138,1655,72)
$g.DrawRectangle([System.Drawing.Pens]::LightSteelBlue,60,1138,1655,72)
$g.DrawString('Para Supabase: UUID, JSONB y TIMESTAMPTZ. Aplicar RLS para que responsables vean sus estudiantes y docentes solo sus grupos activos.',$fontNote,$brushNote,82,1156)
$g.DrawString('El motor adaptativo, la corrección y ML Kit viven en la app; la base registra sus resultados, progreso e intervenciones.',$fontNote,$brushNote,82,1183)

$out = Join-Path (Get-Location) 'diagrama_relacional_kmat.png'
$bitmap.Save($out,[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bitmap.Dispose(); $linePen.Dispose(); $dashPen.Dispose()
