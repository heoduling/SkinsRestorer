import json
import pathlib
import re
import sys
import time
import urllib.request

lab = pathlib.Path(__file__).resolve().parent
log = lab / 'server/logs/latest.log'
phase = sys.argv[1]
fixed = phase.startswith('fixed')
name = 'SkinCheck'
results = []

def post(action, **data):
    request = urllib.request.Request('http://127.0.0.1:53547',
        json.dumps(dict(action=action, name=name, **data)).encode(), {'Content-Type': 'application/json'})
    return json.load(urllib.request.urlopen(request, timeout=10))

def wait(pattern, offset, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        text = log.read_text(encoding='utf-8')[offset:]
        assert '[SkinHolderProbe] FAIL' not in text, text
        if re.search(pattern, text):
            return text
        time.sleep(.1)
    raise AssertionError('Timeout: ' + pattern)

def command(command, pattern):
    offset = len(log.read_text(encoding='utf-8'))
    post('send', command=command)
    return wait(pattern, offset)

def menu():
    text = command('srprobe menu', 'MENU_REPORT')
    time.sleep(.2)
    # Menu slot entries follow the report on the same entity task.
    return log.read_text(encoding='utf-8').rsplit('MENU_REPORT', 1)[1]

def pass_case(label):
    results.append(label)
    print('PASS', label, flush=True)

state = next((b for b in post('status') if b['name'] == name), None)
if not state or state['state'] == 'closed':
    post('connect')
for _ in range(100):
    state = next(b for b in post('status') if b['name'] == name)
    if state['state'] == 'play':
        break
    time.sleep(.1)
assert state['state'] == 'play', state
time.sleep(.5)
command('srprobe run', 'SUITE_PASS cases=144 fixed=' + str(fixed).lower())
pass_case('144 core, ordinary-container and custom-holder event checks')

for container in ['barrel', 'chest', 'double']:
    command('srprobe wire ' + container, 'WIRE_READY')
    time.sleep(.25)
    post('click', slot=0)
    time.sleep(.25)
    report = command('srprobe report ' + container, 'WIRE_REPORT')
    assert 'slot=null cursor=ItemStack{STONE x 32}' in report, report
    if fixed or container == 'double':
        assert 'sr.snapshot=' not in report, report
    else:
        assert 'sr.snapshot=1' in report, report
    pass_case('real-client item transfer and snapshot count: ' + container)

command('srprobe reset', 'RESET_DONE')
command('srprobe clearprofile', 'PROFILE_CLEARED')
post('send', command='skins')
time.sleep(.6)
assert 'MENU_SLOT 20 type=BOOKSHELF' in menu()
pass_case('real /skins selection menu')
post('click', slot=20)
time.sleep(.6)
page = menu()
assert 'MENU_SLOT 0 type=PLAYER_HEAD name=audit00' in page, page
assert 'MENU_SLOT 50 type=ARROW' in page, page
assert len(re.findall(r'MENU_SLOT \d+ type=PLAYER_HEAD', page)) == 36, page
pass_case('real custom-skin first page: 36 entries')
post('click', slot=50)
time.sleep(.6)
page = menu()
assert 'MENU_SLOT 0 type=PLAYER_HEAD name=audit36' in page, page
assert len(re.findall(r'MENU_SLOT \d+ type=PLAYER_HEAD', page)) == 4, page
pass_case('next page: remaining 4 entries')
post('click', slot=48)
time.sleep(.6)
assert 'MENU_SLOT 0 type=PLAYER_HEAD name=audit00' in menu()
pass_case('previous page')
post('click', slot=0)
time.sleep(.8)
profile = command('srprobe profile', 'PROFILE_REPORT')
assert 'identifier=audit00' in profile, profile
assert next(b for b in post('status') if b['name'] == name)['containerId'] == 0
pass_case('skin selection persisted and closed menu')
post('send', command='skins')
time.sleep(.5)
assert 'MENU_SLOT 20 type=BOOKSHELF' in menu()
post('close_window')
pass_case('close and reopen')
command('srprobe stop', 'Stopping server', )
wait('Awaiting termination of I/O pool', 0)
(lab / (phase + '-server.log')).write_text(log.read_text(encoding='utf-8'), encoding='utf-8')
(lab / (phase + '-result.json')).write_text(json.dumps(dict(phase=phase, checks=144,
    client_checks=len(results) - 1, passed=results), indent=2), encoding='utf-8')
print('RUNTIME_PHASE_PASS', phase, flush=True)
