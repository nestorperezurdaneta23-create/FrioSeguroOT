"""Crear el bloc de notas exigido por el AAI, usando un repositorio real."""
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

if len(sys.argv) != 2:
    raise SystemExit('Uso: python scripts/crear_entrega.py https://github.com/USUARIO/FrioSeguroOT')
url=sys.argv[1].strip().rstrip('/')
p=urlparse(url)
if p.scheme != 'https' or p.hostname != 'github.com' or p.query or p.fragment or not re.fullmatch(r'/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',p.path):
    raise SystemExit('Ingresa el enlace HTTPS del repositorio real en github.com.')
if any(x.lower() in ('tu_usuario','usuario','owner','example') for x in p.path.split('/')):
    raise SystemExit('Sustituye el ejemplo por el usuario de tu repositorio real.')
output=Path('Entrega_TI3042_Nestor_Urdaneta.txt')
output.write_text(url+'\n',encoding='utf-8')
print('Creado:',output.resolve())
