/** Modèles de légendes pour la communication d'un club de handball. */

export type TemplateId = 'libre' | 'match' | 'resultat' | 'convocation';

export interface TemplateFields {
  club: string;
  equipe: string;
  adversaire: string;
  domicile: boolean;
  date: string; // AAAA-MM-JJ
  heure: string; // HH:mm
  salle: string;
  competition: string;
  scoreNous: number | null;
  scoreEux: number | null;
  meilleurs: string; // « Lisa (8 buts), Tom (6 arrêts) »
  rdvHeure: string;
  joueurs: string; // un nom par ligne
  hashtags: string;
}

export const TEMPLATES: { id: TemplateId; label: string; hint: string }[] = [
  { id: 'libre', label: 'Libre', hint: 'Rédigez votre texte vous-même' },
  { id: 'match', label: 'Jour de match', hint: 'Annonce avant la rencontre' },
  { id: 'resultat', label: 'Résultat', hint: 'Score et temps forts' },
  { id: 'convocation', label: 'Convocation', hint: 'Liste des joueurs et rendez-vous' },
];

export function emptyFields(): TemplateFields {
  return {
    club: '',
    equipe: 'Seniors',
    adversaire: '',
    domicile: true,
    date: '',
    heure: '',
    salle: '',
    competition: '',
    scoreNous: null,
    scoreEux: null,
    meilleurs: '',
    rdvHeure: '',
    joueurs: '',
    hashtags: '#handball #hand #sportamateur',
  };
}

function formatDate(iso: string): string {
  if (!iso) return '';
  const d = new Date(`${iso}T12:00:00`);
  return d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
}

function affiche(f: TemplateFields): string {
  const club = f.club || 'Nous';
  return f.domicile ? `${club} 🆚 ${f.adversaire || '…'}` : `${f.adversaire || '…'} 🆚 ${club}`;
}

function lieu(f: TemplateFields): string {
  const parts = [f.domicile ? 'à domicile' : 'à l’extérieur', f.salle].filter(Boolean);
  return parts.join(' · ');
}

export function buildCaption(id: TemplateId, f: TemplateFields): string {
  const lines: string[] = [];
  const quand = [formatDate(f.date), f.heure && `${f.heure.replace(':', 'h')}`].filter(Boolean).join(' à ');

  switch (id) {
    case 'match':
      lines.push(`🤾 JOUR DE MATCH · ${f.equipe}`.trim());
      lines.push('');
      lines.push(affiche(f));
      if (f.competition) lines.push(`🏆 ${f.competition}`);
      if (quand) lines.push(`🗓️ ${quand}`);
      lines.push(`📍 ${lieu(f)}`);
      lines.push('');
      lines.push(f.domicile ? 'Venez nombreux pour pousser l’équipe ! 📣' : 'Tous derrière nos joueurs, même à distance ! 💪');
      break;

    case 'resultat': {
      const nous = f.scoreNous ?? 0;
      const eux = f.scoreEux ?? 0;
      const titre = nous > eux ? '✅ VICTOIRE' : nous < eux ? '❌ DÉFAITE' : '🤝 MATCH NUL';
      lines.push(`${titre} · ${f.equipe}`.trim());
      lines.push('');
      lines.push(`${f.club || 'Nous'} ${nous} - ${eux} ${f.adversaire || '…'}`);
      if (f.competition) lines.push(`🏆 ${f.competition}`);
      if (f.meilleurs) lines.push(`⭐ ${f.meilleurs}`);
      lines.push('');
      lines.push(
        nous > eux
          ? 'Bravo à tout le groupe et merci au public ! 🔥'
          : nous < eux
            ? 'On relève la tête et on revient plus forts au prochain match ! 💪'
            : 'Un point arraché ensemble, on continue ! 👊',
      );
      break;
    }

    case 'convocation': {
      lines.push(`📋 CONVOCATION · ${f.equipe}`.trim());
      lines.push('');
      lines.push(affiche(f));
      if (quand) lines.push(`🗓️ ${quand}`);
      if (f.rdvHeure) lines.push(`⏰ Rendez-vous à ${f.rdvHeure.replace(':', 'h')}`);
      lines.push(`📍 ${lieu(f)}`);
      const joueurs = f.joueurs.split('\n').map((j) => j.trim()).filter(Boolean);
      if (joueurs.length) {
        lines.push('');
        lines.push('Sont convoqués :');
        joueurs.forEach((j) => lines.push(`▫️ ${j}`));
      }
      break;
    }

    case 'libre':
      return '';
  }

  if (f.hashtags.trim()) {
    lines.push('');
    lines.push(f.hashtags.trim());
  }
  return lines.join('\n');
}
