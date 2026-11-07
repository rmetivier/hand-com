import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { buildCaption, emptyFields } from './core/templates';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
  });

  it('se crée', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });
});

describe('Modèles de légendes', () => {
  it('annonce une victoire', () => {
    const f = { ...emptyFields(), club: 'HBC Test', adversaire: 'AS Voisins', scoreNous: 28, scoreEux: 24 };
    const text = buildCaption('resultat', f);
    expect(text).toContain('VICTOIRE');
    expect(text).toContain('HBC Test 28 - 24 AS Voisins');
  });

  it('liste les joueurs convoqués', () => {
    const f = { ...emptyFields(), joueurs: 'Lisa\n\nTom ' };
    const text = buildCaption('convocation', f);
    expect(text).toContain('▫️ Lisa');
    expect(text).toContain('▫️ Tom');
  });
});
